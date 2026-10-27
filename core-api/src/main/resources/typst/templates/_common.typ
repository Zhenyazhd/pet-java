// Shared by every template in this folder. ResumeTypstRenderer puts it, after the line that binds the resume to `r`,
// before the template. Everything here reads the resume as data (strings); nothing a user types becomes markup.
#let layout = r.at("layout", default: (:))
#let lv(key, default) = float(layout.at(key, default: default))
// With autoFit off the worker's scale is ignored, so the sizes are exactly what the user typed.
#let scale = if layout.at("autoFit", default: true) { float(sys.inputs.at("scale", default: "1.0")) } else { 1.0 }
#let en = lower(r.at("locale", default: "")) == "en"
#let lang = if en { "en" } else { "fr" }
// French sets a (non-breaking) space before a colon; English does not.
#let colon = if en { ":" } else { "\u{a0}:" }
#let titles = if en {
  (profile: "Profile", experience: "Professional Experience", education: "Education", achievements: "Achievements", skills: "Skills")
} else {
  (profile: "Profil", experience: "Expérience professionnelle", education: "Formation", achievements: "Réalisations", skills: "Compétences")
}

// A very long unbroken word (a URL, a stray paste) would run past the margin and be clipped: let it break.
#show regex("\\S{60,}"): it => it.text.clusters().chunks(40).map(chunk => chunk.join()).join(sym.zws)

// Only web and mail links are made clickable; a bare "linkedin.com/in/x" is read as https.
#let safe-link(url, label) = {
  let u = url.trim()
  let lowered = lower(u)
  if lowered.starts-with("http://") or lowered.starts-with("https://") or lowered.starts-with("mailto:") {
    link(u)[#label]
  } else if u.contains(".") and not u.contains(":") and not u.starts-with("/") and not u.contains(" ") {
    link("https://" + u)[#label]
  } else {
    label
  }
}

// The contact parts that exist, as an array (for a rail) and as one line separated by bars.
#let contacts = (
  if r.phone.trim() != "" { r.phone },
  if r.email.trim() != "" { safe-link("mailto:" + r.email, r.email) },
  if r.linkedinUrl.trim() != "" { safe-link(r.linkedinUrl, if r.linkedinLabel.trim() != "" { r.linkedinLabel } else { r.linkedinUrl }) },
).filter(part => part != none)
#let contact-line = contacts.join([#h(0.5em)#sym.bar.v#h(0.5em)])

#let filled(items) = items.filter(item => item.trim() != "")
// A job's bullets as one list, or nothing when it has none.
#let bullet-list(items) = if filled(items).len() > 0 { list(..filled(items).map(item => [#item])) } else { [] }
#let skill-lines = r.skills.filter(s => s.category.trim() != "" or s.items.trim() != "")
#let achievement-items = r.achievements.filter(a => a.title.trim() != "" or a.text.trim() != "")
#let edu-place(edu) = (edu.subtitle, edu.location).filter(part => part.trim() != "").join(", ")
// The skills as "Category: items" lines.
#let skills-text = for s in skill-lines { [#strong[#s.category#colon] #s.items]; parbreak() }
#let achievements-text = for a in achievement-items { [#strong[#a.title#colon] #a.text]; parbreak() }

// Profile, experience and education for a template made of `section(title)` and
// `entry(title:, subtitle:, dates:, location:, meta:, body)`; the rest is `flat-body` or a rail's job.
#let main-body(section, entry) = {
  if r.profile.trim() != "" { section(titles.profile); r.profile }
  section(titles.experience)
  for job in r.experience {
    entry(title: job.title, subtitle: job.subtitle, dates: job.dates, location: "", meta: "", bullet-list(job.bullets))
  }
  if r.education.len() > 0 {
    section(titles.education)
    for edu in r.education {
      entry(title: edu.title, subtitle: edu-place(edu), dates: "", location: "", meta: "", if edu.details.trim() != "" { edu.details } else { [] })
    }
  }
}
// Achievements and skills, one after the other (a rail puts them beside the main column instead).
#let side-body(section) = {
  if achievement-items.len() > 0 { section(titles.achievements); achievements-text }
  if skill-lines.len() > 0 { section(titles.skills); skills-text }
}
#let flat-body(section, entry) = {
  main-body(section, entry)
  side-body(section)
}
