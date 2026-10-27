// Column CV, a Typst resume template.
//
// Compiles with Typst 0.12 or newer.
// Set in Raleway. Without it installed the text falls back;
// change the font argument in the show rule below to a family you
// do have, the layout does not depend on it.
//
// Licence: MIT-0. Use it, change it, publish the result as your own CV.
// No attribution has to travel with the document you make from it.
//
// Self-contained: no imports, nothing to install.
// Every design:     https://github.com/davidculemann/typst-templates
// Edit in a browser: https://www.jobsprout.ai/resume-templates/column
#set page(fill: rgb("#ffffff"))

#let is-blank(v) = v == none or v == "" or v == []

// One accent + ink + one muted grey (cv-typography-standard rule 6). Uncolored,
// ATS-safe: no filled panels, text is ink on white, the accent tints only the
// masthead rule and the section labels/rules. Default accent a professional blue.
#let ink = rgb("#1f2430")
#let muted = rgb("#6b7280")

// Fixed size scale from body B = 10pt (standard rule 2), defined ONCE.
#let display-size = 20pt // ~2.0 x B - the name (masthead)
#let subhead-size = 11.5pt // ~1.15 x B - the profession line
#let body-size = 10pt // B
#let label-size = 9pt // ~0.9 x B - section labels (the one uppercase + tracked role)
#let meta-size = 8.2pt // ~0.82 x B - dates, location, contact, level, fine print

// Document shell: set-rules + PDF metadata. A full-width masthead sits above an
// uncolored two-column grid (narrow rail + wide main), both composed by wrapSections.
// Raleway is the single family (standard rule 1); regular + semibold + bold.
#let column-cv(
  author: "",
  font: "Raleway",
  font-size: 10pt,
  paper: "a4",
  margin: 1.4cm,
  leading: 0.62em,
  lang: "en",
  body,
) = {
  let author-str = if type(author) == str { author } else { "CV" }
  set document(author: author-str, title: author-str + " - CV")

  // Ragged-right, no hyphenation: the narrow main + rail columns otherwise break words
  // ("architec-ture", "Post-gres") on nearly every line.
  set text(font: font, size: font-size, fill: ink, lang: lang, ligatures: false, hyphenate: false)
  set page(margin: margin, paper: paper)
  set par(justify: false, leading: leading)

  show link: set text(fill: muted)

  body
}

// Masthead (full width, above the two columns): name (bold) + profession (muted),
// then an accent rule. Guarded so an empty CV shows no floating rule.
#let masthead(author: "", profession: "", accent-color: "#2b4c7e") = {
  let has-name = not is-blank(author)
  let has-prof = not is-blank(profession)
  if has-name or has-prof {
    block(above: 0pt, below: 1.1em, breakable: false, {
      set block(spacing: 0pt)
      set par(spacing: 0pt, leading: 0.4em, justify: false)
      if has-name { text(size: display-size, weight: "bold", fill: ink, author) }
      if has-prof {
        if has-name { v(3pt) }
        text(size: subhead-size, weight: "regular", fill: muted, profession)
      }
      v(6pt)
      line(length: 100%, stroke: 1pt + rgb(accent-color))
    })
  }
}

// Section label (rail + main share it): accent UPPERCASE + tracking over a thin
// accent rule. Sticky so a label never orphans at a page bottom.
#let cv-section(title, accent-color: "#2b4c7e") = {
  block(above: 1.2em, below: 0.9em, sticky: true, {
    set block(spacing: 0pt)
    set par(spacing: 0pt, justify: false)
    text(size: label-size, weight: "semibold", tracking: 0.8pt, fill: rgb(accent-color), upper(title))
    v(3pt)
    line(length: 100%, stroke: 0.6pt + rgb(accent-color))
  })
}

// Rail contact: a CONTACT label then one muted line per item (email, phone,
// location, social), a quiet legible stack.
#let rail-contact(items, accent-color: "#2b4c7e") = {
  cv-section("Contact", accent-color: accent-color)
  // sectionSpacing must not leak into the contact rows (explicit below controls them).
  set block(spacing: 0pt)
  set par(spacing: 0pt)
  for item in items {
    block(above: 0pt, below: 4pt, breakable: false, {
      set text(size: meta-size, fill: muted)
      item
    })
  }
}

// Rail language: language with the proficiency level (muted) STACKED beneath it.
// Stacked, not a two-column row: in the narrow rail a long level would squeeze the
// language onto broken, hyphenated lines. No hyphenation on the language name.
#let column-language(language: "", level: "") = {
  block(breakable: false, {
    set block(spacing: 0pt)
    set par(spacing: 0pt, justify: false)
    set text(hyphenate: false)
    if not is-blank(language) { text(fill: ink, language) }
    if not is-blank(level) {
      if not is-blank(language) { v(1pt) }
      text(size: meta-size, fill: muted, level)
    }
  })
}

// Rail credential (certifications / awards in the narrow rail): compact stacked -
// name (semibold ink), then issuer + date muted beneath on one line, dot-separated.
// No bullets and no wide date grid, so it fills the rail cleanly without cramping.
#let column-rail-cred(name: "", issuer: "", date: "") = {
  block(breakable: false, {
    set block(spacing: 0pt)
    set par(spacing: 0pt, justify: false)
    set text(hyphenate: false)
    if not is-blank(name) { text(weight: "semibold", fill: ink, name) }
    let meta = (issuer, date).filter(v => not is-blank(v)).join(text(fill: muted)[ #sym.dot.c ])
    if not is-blank(meta) { v(2pt); text(size: meta-size, fill: muted, meta) }
  })
}

// Main entry: semibold-ink title leads with a muted subtitle, dates right; location
// and an optional URL meta line below; then the body. No mid-word hyphenation on the
// title line (wraps at spaces); date top-aligned beside the first title line.
#let column-entry(title: "", subtitle: "", dates: "", location: "", meta: "", body) = {
  // No explicit above/below: the ENTRY-to-entry gap rides the ambient block spacing,
  // which the sectionSpacing knob scales (tight 0.9em < default 1.2em < relaxed 1.5em).
  // The reset below fixes the INTERNAL header->body gap to the explicit v() so the knob
  // never balloons or inverts it.
  block(breakable: false, {
    set block(spacing: 0pt)
    set par(spacing: 0pt)
    block(sticky: true, breakable: false, {
      set par(justify: false)
      set text(hyphenate: false)
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

// Two-column body: a narrow rail (contact, skills, languages) beside the wide main
// column (summary, experience, ...). A breakable grid so a long main paginates. A
// thin full-height divider (grid.vline, redrawn on every page the grid spans) makes
// the rail read as a deliberate column, so when it empties on page 2+ the left is a
// clean empty rail rather than a stranded dead margin.
#let cv-layout(rail: [], main: [], gap: 20pt, accent-color: "#2b4c7e") = {
  grid(
    columns: (30%, 1fr),
    column-gutter: gap,
    rail,
    // The divider is the LEFT BORDER of the main column - a lightened tint of the
    // single accent (never a separate grey), with a left inset so it never touches the
    // text. It spans the main content height and repaints on every page, so when the
    // rail empties on page 2+ the divider still reads as a deliberate column edge.
    block(
      width: 100%,
      inset: (left: 16pt),
      stroke: (left: 0.8pt + rgb(accent-color).lighten(55%)),
      main,
    ),
  )
}

// ---------------------------------------------------------------------------------------------------------------
// Adapted for this project: the sample document is replaced by the resume `r`, and the page settings of the
// editor (font size, line spacing, left/right margin) override the template's own values when they are set.

#show: column-cv.with(
  author: r.name,
  font: "Raleway",
  font-size: lv("fontSize", 10) * 1pt * scale,
  paper: "a4",
  margin: lv("marginX", 1.4) * 1cm,
  leading: lv("lineSpacing", 0.62) * 1em,
  lang: lang,
)
#masthead(author: r.name, profession: r.headline, accent-color: "#2b4c7e")
#cv-layout(
  rail: [#rail-contact(contacts, accent-color: "#2b4c7e")#side-body(cv-section)],
  main: [#main-body(cv-section, column-entry)],
)
