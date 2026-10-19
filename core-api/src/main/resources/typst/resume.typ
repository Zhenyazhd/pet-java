// Resume template. ResumeTypstRenderer puts one line before it:
//   #let r = json(bytes("<the resume as JSON>"))
// so every text below is data (a string), never markup. `scale` (sys.inputs) is set by the pdf worker's page-fit search.
#let scale = float(sys.inputs.at("scale", default: "1.0"))
#let accent = rgb("#1F3B57")
#let soft = rgb("#5A5A5A")
#let size = 9.7pt * scale
// French sets a (non-breaking) space before a colon; English does not.
#let colon = if lower(r.at("locale", default: "")) == "en" { ":" } else { "\u{a0}:" }

#let titles = if lower(r.at("locale", default: "")) == "en" {
  (profile: "Profile", experience: "Professional Experience", education: "Education", achievements: "Achievements", skills: "Skills")
} else {
  (profile: "Profil", experience: "Expérience professionnelle", education: "Formation", achievements: "Réalisations", skills: "Compétences")
}

#set document(title: r.name, author: r.name)
#set page(paper: "a4", margin: (x: 1.2cm, top: 0.8cm, bottom: 0.75cm), numbering: none)
#set text(font: "Libertinus Serif", size: size, lang: lower(r.at("locale", default: "fr")), fill: black)
#set par(leading: 0.55em, spacing: 0.55em, justify: false)
#show link: set text(fill: accent)
// A very long unbroken word (a URL, a stray paste) would run past the margin and be clipped: let it break.
#show regex("\\S{60,}"): it => it.text.clusters().chunks(40).map(chunk => chunk.join()).join(sym.zws)

#let section(title) = {
  block(above: 0.95em, below: 0.45em, width: 100%, sticky: true)[
    #text(weight: "bold", fill: accent, size: size * 1.18)[#title]
    #v(-0.1em)
    #line(length: 100%, stroke: 0.9pt + accent)
  ]
}

#let entry(title, sub, aside) = {
  block(above: 0.7em, below: 0em, breakable: false, sticky: true)[
    #grid(columns: (1fr, auto), column-gutter: 0.6em,
      text(weight: "bold")[#title],
      text(fill: soft, size: size * 0.9)[#aside])
    #if sub != "" [
      #v(0em)
      #text(style: "italic", size: size * 0.9)[#sub]
    ]
  ]
}

#let bullets(items) = {
  if items.len() > 0 {
    block(above: 0.3em, below: 0em)[
      #for item in items [
        #grid(columns: (0.9em, 1fr), text(fill: soft)[•], [#item])
        #v(0.2em)
      ]
    ]
  }
}

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

// Header: only the contact parts that exist, separated by bars
#let contacts = (
  if r.phone.trim() != "" { r.phone },
  if r.email.trim() != "" { safe-link("mailto:" + r.email, r.email) },
  if r.linkedinUrl.trim() != "" { safe-link(r.linkedinUrl, if r.linkedinLabel.trim() != "" { r.linkedinLabel } else { r.linkedinUrl }) },
).filter(part => part != none)

#align(center)[
  #text(size: size * 1.85, weight: "bold", fill: accent)[#r.name]
  #v(-0.05em)
  #text(fill: soft, size: size * 1.05)[#r.headline]
  #v(0em)
  #text(size: size * 0.9)[#contacts.join([#h(0.5em)|#h(0.5em)])]
]

#if r.profile.trim() != "" [
  #section(titles.profile)
  #r.profile
]

#section(titles.experience)
#for job in r.experience {
  entry(job.title, job.subtitle, job.dates)
  bullets(job.bullets.filter(b => b.trim() != ""))
}

#if r.education.len() > 0 [
  #section(titles.education)
  #for edu in r.education {
    // The place goes on the second line with the institution, so a parser cannot read it as part of the degree.
    let place = (edu.subtitle, edu.location).filter(part => part.trim() != "").join(", ")
    entry(edu.title, place, "")
    if edu.details.trim() != "" {
      block(above: 0.3em)[#text(size: size * 0.9)[#edu.details]]
    }
  }
]

#let achievements = r.achievements.filter(a => a.title.trim() != "" or a.text.trim() != "")
#if achievements.len() > 0 [
  #section(titles.achievements)
  #bullets(achievements.map(a => [*#a.title#colon* #a.text]))
]

#let skills = r.skills.filter(s => s.category.trim() != "" or s.items.trim() != "")
#if skills.len() > 0 [
  #section(titles.skills)
  #for s in skills [
    #block(above: 0.5em)[*#s.category#colon* #s.items]
  ]
]
