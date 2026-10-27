// Creative CV, a Typst resume template.
//
// Compiles with Typst 0.12 or newer.
// Set in HK Grotesk. Without it installed the text falls back;
// change the font argument in the show rule below to a family you
// do have, the layout does not depend on it.
//
// Derived from grotesk-cv (https://github.com/AsiSkarp/grotesk-cv),
// (c) its authors, used under Unlicense. That licence continues to apply,
// so this file is not MIT-0: keep this notice if you redistribute the template
// itself. A CV you write with it is your own work.
// Modified from the original by JobSprout: restructured for a section registry,
// re-typeset to a fixed size scale, and reworked for empty-field handling.
//
// Self-contained: no imports, nothing to install.
// Every design:     https://github.com/davidculemann/typst-templates
// Edit in a browser: https://www.jobsprout.ai/resume-templates/creative
#set page(fill: rgb("#f4f1eb"))

#let textColor = rgb("#3c3c42")
#let secondaryTextColor = rgb("#78787e")
#let accentColor = rgb("#e5e3de")
#let backgroundColor = rgb("#f4f1eb")

// Creative CV Base Template Functions

// FontAwesome icon function using ligatures
#let fa-icon(name, solid: true) = []

// Color definitions are injected by generateDocument before this template
// so the macros below capture the user-resolved values.

// Contact info item with icon
#let contactItem(icon, content) = {
  text(
    size: 10pt,
    fill: secondaryTextColor,
    weight: "medium",
  )[
    #fa-icon(icon, solid: true) #h(10pt) #content
  ]
}

// Section header
#let sectionHeader(title, icon: none) = {
  v(6pt)
  text(
    size: 12pt,
    weight: "bold",
    fill: textColor,
  )[
    #if icon != none [#fa-icon(icon) #h(5pt)]
    #title
  ]
  v(3pt)
}

#let is-blank(v) = v == none or v == "" or v == []

// Experience/Education entry
#let cvEntry(
  title: "",
  subtitle: none,
  date: "",
  location: "",
  description: "",
) = {
  // Title and date row
  if not (is-blank(title) and is-blank(date)) {
    table(
      columns: (3fr, 1fr),
      inset: 0pt,
      stroke: none,
      [#text(size: 10pt, weight: "bold", fill: textColor)[#title]],
      [#text(
        size: 9pt,
        weight: "bold",
        style: "italic",
        fill: textColor,
      )[#date]],
    )
  }

  // Subtitle/location row (only if subtitle or location exists)
  if not (is-blank(subtitle) and is-blank(location)) {
    v(-2pt)
    text(
      size: 10pt,
      style: "italic",
      weight: "medium",
      fill: textColor,
    )[#if not is-blank(subtitle) [#subtitle]#if not is-blank(subtitle) and not is-blank(location) [, ]#if not is-blank(location) [#location]]
  }

  // Description
  if not is-blank(description) {
    v(5pt)
    text(size: 10pt, fill: textColor)[#description]
  }

  v(2pt)
}

// Skill tag
#let skillTag(skill) = {
  box(
    inset: (x: 0.3em),
    outset: (y: 0.2em),
    fill: accentColor,
    radius: 3pt,
    text(weight: "bold", skill)
  )
}

// Sidebar entry for publications, certifications, awards
// Table layout with title/org/link on left, date on right
#let sidebarEntry(
  title: "",
  organization: "",
  link-url: "",
  link-name: "",
  date: "",
  description: "",
) = {
  let lines = ()
  if not is-blank(title) { lines.push(text(size: 10pt, weight: "bold", fill: textColor)[#title]) }
  let parts = ()
  if not is-blank(organization) { parts.push(organization) }
  // Use link-name as display text if available, otherwise use URL
  if link-name != "" and link-url != "" {
    parts.push(link(link-url)[#link-name])
  } else if link-url != "" {
    parts.push(link(link-url))
  } else if link-name != "" {
    parts.push(link-name)
  }
  if parts.len() > 0 { lines.push(text(size: 9pt, fill: secondaryTextColor)[#parts.join(", ")]) }
  if not is-blank(description) { lines.push([#v(2pt) #description]) }

  table(
    columns: (1fr, auto),
    inset: 0pt,
    stroke: none,
    column-gutter: 8pt,
    align: (left, right),
    [#lines.join(linebreak())],
    [#text(size: 8.5pt, weight: "medium", fill: secondaryTextColor, style: "italic")[#date]],
  )
  v(4pt)
}

// Reference entry - compact 3-line format without icons
#let referenceEntry(
  name: "",
  role: "",
  organization: "",
  email: "",
  phone: "",
) = {
  let lines = ()
  if not is-blank(name) { lines.push(text(size: 9.5pt, weight: "bold", fill: textColor)[#name]) }
  let parts = ()
  if not is-blank(role) { parts.push(role) }
  if not is-blank(organization) { parts.push(organization) }
  if parts.len() > 0 { lines.push(text(size: 9pt, fill: secondaryTextColor)[#parts.join(", ")]) }
  let contacts = ()
  if email != "" { contacts.push(email) }
  if phone != "" { contacts.push(phone) }
  if contacts.len() > 0 { lines.push(text(size: 8.5pt, fill: secondaryTextColor)[#contacts.join(" • ")]) }
  lines.join(linebreak())
  v(6pt)
}

// Document show function (plan 099 Phase 4c). Owns the document chrome that the
// hand-written generator emitted inline: document metadata, the text/page/par
// set rules, and the name + contact-grid header. Colors are captured from the
// prelude-injected globals (textColor/secondaryTextColor/accentColor/
// backgroundColor). social-links tuples are (url, display, icon-name).
#let creative-cv(
  author: [],
  author-str: "",
  location: "",
  email: "",
  phone: "",
  social-links: (),
  font: "Hanken Grotesk",
  font-fallback: (),
  font-size: 10pt,
  paper: "a4",
  margin: (left: 1.5cm, right: 1.5cm, top: 1.5cm, bottom: 1.5cm),
  leading: 0.65em,
  lang: "en",
  body,
) = {
  set document(author: author-str, title: author-str + " - CV")

  set text(
    font: (font, "Source Sans 3", ..font-fallback),
    weight: "regular",
    size: font-size,
    fill: textColor,
    lang: lang,
    hyphenate: true,
  )

  set page(
    paper: paper,
    margin: margin,
    fill: backgroundColor,
  )

  set par(justify: false, leading: leading)

  let contact-items = ()
  if location != "" and location != none { contact-items.push(contactItem("location-dot", location)) }
  if email != "" and email != none { contact-items.push(contactItem("envelope", email)) }
  if phone != "" and phone != none { contact-items.push(contactItem("phone", phone)) }
  for social-link in social-links {
    let (url, display-text, icon-name) = social-link
    contact-items.push(contactItem(icon-name, link(url)[#display-text]))
  }

  block(
    width: 100%,
    breakable: false,
  )[
    #text(size: 30pt, weight: "extrabold", fill: textColor)[#author]
    #if contact-items.len() > 0 {
      v(8pt)
      table(
        columns: (1fr, 1fr),
        stroke: none,
        inset: 0pt,
        row-gutter: 6pt,
        ..contact-items,
      )
    }
  ]

  v(12pt)

  body
}

// ---------------------------------------------------------------------------------------------------------------
// Adapted for this project: the sample document is replaced by the resume `r`, and the page settings of the
// editor (font size, line spacing, left/right margin) override the template's own values when they are set.

#let entry(title: "", subtitle: "", dates: "", location: "", meta: "", body) = cvEntry(title: title, subtitle: subtitle, date: dates, location: location, description: body)
#show: creative-cv.with(
  author: [#r.name],
  author-str: r.name,
  email: r.email,
  phone: r.phone,
  social-links: if r.linkedinUrl.trim() != "" { ((r.linkedinUrl, if r.linkedinLabel.trim() != "" { r.linkedinLabel } else { r.linkedinUrl }, "linkedin"),) } else { () },
  font: "Hanken Grotesk",
  font-size: lv("fontSize", 10) * 1pt * scale,
  paper: "a4",
  margin: (left: lv("marginX", 1.5) * 1cm, right: lv("marginX", 1.5) * 1cm, top: lv("marginTop", 1.5) * 1cm, bottom: lv("marginBottom", 1.5) * 1cm),
  leading: lv("lineSpacing", 0.65) * 1em,
  lang: lang,
)
#if r.headline.trim() != "" { text(size: 12pt, weight: "medium", fill: secondaryTextColor)[#r.headline] }
#flat-body(sectionHeader, entry)
