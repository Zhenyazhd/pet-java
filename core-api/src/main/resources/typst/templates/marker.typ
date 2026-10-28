// Marker CV, a Typst resume template.
//
// Compiles with Typst 0.12 or newer.
// Set in Source Sans 3. Without it installed the text falls back;
// change the font argument in the show rule below to a family you
// do have, the layout does not depend on it.
//
// Licence: MIT-0. Use it, change it, publish the result as your own CV.
// No attribution has to travel with the document you make from it.
//
// Self-contained: no imports, nothing to install.
// Every design:     https://github.com/davidculemann/typst-templates
// Edit in a browser: https://www.jobsprout.ai/resume-templates/marker
#set page(fill: rgb("#ffffff"))

#let is-blank(v) = v == none or v == "" or v == []

// One accent + ink + one muted grey (cv-typography-standard rule 6). The accent
// appears as a small filled TAB beside each section label (its distinct placement);
// labels are ink, body ink on white.
#let ink = rgb("#1f2430")
#let muted = rgb("#6b7280")

// Fixed size scale from body B = 10.5pt (standard rule 2), defined ONCE.
#let display-size = 21pt // ~2.0 x B - the name
#let subhead-size = 12pt // ~1.14 x B - the profession line
#let body-size = 10.5pt // B
#let label-size = 10pt // ~0.95 x B - section labels (the one uppercase + tracked role)
#let meta-size = 8.6pt // ~0.82 x B - dates, location, contact, level, fine print

// Document shell: set-rules + PDF metadata. Source Sans 3 is the single family
// (standard rule 1); regular + semibold + bold.
#let marker-cv(
  author: "",
  font: "Source Sans 3",
  font-size: 10.5pt,
  paper: "a4",
  margin: 1.5cm,
  leading: 0.65em,
  lang: "en",
  body,
) = {
  let author-str = if type(author) == str { author } else { "CV" }
  set document(author: author-str, title: author-str + " - CV")

  set text(font: font, size: font-size, fill: ink, lang: lang, ligatures: false, hyphenate: false)
  set page(margin: margin, paper: paper)
  set par(justify: false, leading: leading)

  show link: set text(fill: muted)

  body
}

// Masthead: name (bold) + profession (muted) + a quiet contact line, then an accent
// rule. Guarded so an empty CV shows no floating rule.
#let masthead(author: "", profession: "", contact: "", accent-color: "#2c5f6e") = {
  let has-name = not is-blank(author)
  let has-prof = not is-blank(profession)
  let has-contact = not is-blank(contact)
  if has-name or has-prof or has-contact {
    block(above: 0pt, below: 1.1em, breakable: false, {
      set block(spacing: 0pt)
      set par(spacing: 0pt, leading: 0.45em, justify: false)
      // A short accent TAB to the left of the name, echoing the section-label tabs -
      // the accent's placement. No rule (the tabs carry the accent rhythm).
      if has-name {
        grid(
          columns: (auto, 1fr),
          column-gutter: 10pt,
          align: (left + horizon, left + horizon),
          box(width: 5pt, height: display-size * 0.8, radius: 1pt, fill: rgb(accent-color)),
          text(size: display-size, weight: "bold", fill: ink, author),
        )
      }
      if has-prof {
        if has-name { v(headline-gap(9pt)) }
        text(size: subhead-size, weight: "regular", fill: muted, profession)
      }
      if has-contact {
        if has-name or has-prof { v(6pt) }
        text(size: meta-size, fill: muted, contact)
      }
    })
  }
}

// Section label (the signature): a small filled accent TAB to the left of the
// UPPERCASE label, then a faint hairline rule. The tab is the accent's placement;
// the label itself is ink. Sticky.
#let cv-section(title, accent-color: "#2c5f6e") = {
  block(above: 1.25em, below: 0.9em, sticky: true, {
    set block(spacing: 0pt)
    set par(spacing: 0pt, justify: false)
    grid(
      columns: (auto, 1fr),
      column-gutter: 8pt,
      align: (left + horizon, left + horizon),
      box(width: 4pt, height: label-size, radius: 1pt, fill: rgb(accent-color)),
      text(size: label-size, weight: "semibold", fill: ink, upper(title)),
    )
    v(3pt)
    line(length: 100%, stroke: 0.5pt + rgb(accent-color).lighten(45%))
  })
}

// Entry: semibold-ink title + muted subtitle, dates right; location + optional URL
// meta below; then the body. Title line does not hyphenate; date top-aligned.
#let marker-entry(title: "", subtitle: "", dates: "", location: "", meta: "", body) = {
  block(breakable: false, {
    set block(spacing: 0pt)
    set par(spacing: 0pt)
    block(sticky: true, breakable: false, {
      set par(justify: false)
      grid(
        columns: (1fr, auto),
        align: (left + top, right + top),
        column-gutter: 0.75em,
        {
          if not is-blank(title) { text(weight: "semibold", fill: ink, title) }
          if not is-blank(title) and not is-blank(subtitle) { text(fill: muted)[ #sym.dash.en ] }
          if not is-blank(subtitle) { text(fill: muted, subtitle) }
        },
        if not is-blank(dates) { text(size: meta-size, fill: muted, dates) } else { [] },
      )
      if not is-blank(location) {
        v(3pt)
        text(size: meta-size, fill: muted, location)
      }
      if not is-blank(meta) {
        v(3pt)
        text(size: meta-size, fill: muted, meta)
      }
    })
    if not is-blank(body) {
      v(3.5pt)
      block(spacing: 0pt, { set text(fill: ink); body })
    }
  })
}

// Language row: language left, level muted right.
#let marker-language(language: "", level: "") = {
  grid(
    columns: (1fr, auto),
    align: (left + horizon, right + horizon),
    column-gutter: 0.6em,
    language,
    if not is-blank(level) { text(size: meta-size, fill: muted, level) } else { [] },
  )
}

// ---------------------------------------------------------------------------------------------------------------
// Adapted for this project: the sample document is replaced by the resume `r`, and the page settings of the
// editor (font size, line spacing, left/right margin) override the template's own values when they are set.

#show: marker-cv.with(
  author: r.name,
  font: "Source Sans 3",
  font-size: lv("fontSize", 10) * 1pt * scale,
  paper: "a4",
  margin: lv("marginX", 1.5) * 1cm,
  leading: lv("lineSpacing", 0.65) * 1em,
  lang: lang,
)
#show: apply-spacing
#masthead(author: r.name, profession: headline, contact: contact-line, accent-color: "#2c5f6e")
#flat-body(cv-section, marker-entry)
