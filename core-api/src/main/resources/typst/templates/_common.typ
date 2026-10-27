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

// The settings every template honours besides font size, line spacing and side margins; one that is missing keeps the
// template's own look. A template applies this right after its own setup (`#show: apply-spacing`).
#let apply-spacing(body) = {
  set par(spacing: lv("paragraphSpacing", 0) * 1em) if "paragraphSpacing" in layout
  set list(spacing: lv("bulletSpacing", 0) * 1em) if "bulletSpacing" in layout
  body
}

// Space between the name and the job title line: the user's, or the template's own.
#let headline-gap(default) = if "headlineSpacing" in layout { lv("headlineSpacing", 0) * 1em } else { default }

// A template's section title with the user's gap above it when they set one, else the template's own.
#let section-gap(section) = if "sectionSpacing" in layout { title => section(title, gap: lv("sectionSpacing", 0) * 1em) } else { section }
#let headline = r.headline.trim()

#let filled(items) = items.filter(item => item.trim() != "")
// A job's bullets as one list, or nothing when it has none.
#let bullet-list(items) = if filled(items).len() > 0 {
  if "bulletsTopSpacing" in layout { v(lv("bulletsTopSpacing", 0) * 1em) }
  list(..filled(items).map(item => [#item]))
} else { [] }
#let skill-lines = r.skills.filter(s => s.category.trim() != "" or s.items.trim() != "")
#let achievement-items = r.achievements.filter(a => a.title.trim() != "" or a.text.trim() != "")
#let edu-place(edu) = (edu.subtitle, edu.location).filter(part => part.trim() != "").join(", ")
// The skills as "Category: items" lines.
// The lines of Skills and Achievements, with the user's space between them when it is set.
#let item-lines(items) = for (i, item) in items.enumerate() {
  if "itemSpacing" in layout {
    block(spacing: 0pt, item)
    if i < items.len() - 1 { v(lv("itemSpacing", 0) * 1em) }
  } else { item; parbreak() }
}
#let skills-text = item-lines(skill-lines.map(s => [#strong[#s.category#colon] #s.items]))
#let achievements-text = item-lines(achievement-items.map(a => [#strong[#a.title#colon] #a.text]))

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
