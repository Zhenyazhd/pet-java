// Terrace CV, a Typst resume template.
//
// Compiles with Typst 0.12 or newer.
// Set in EB Garamond. Without it installed the text falls back;
// change the font argument in the show rule below to a family you
// do have, the layout does not depend on it.
//
// Licence: MIT-0. Use it, change it, publish the result as your own CV.
// No attribution has to travel with the document you make from it.
//
// Self-contained: no imports, nothing to install.
// Every design:     https://github.com/davidculemann/typst-templates
// Edit in a browser: https://www.jobsprout.ai/resume-templates/terrace
#set page(fill: rgb("#ffffff"))

// Shared predicate for values that render nothing. Apply BEFORE wrapping a
// value in strong()/emph()/text(): styled empty content is not blank.
// Byte-synced with TYPST_IS_BLANK in utils/typst-blocks.ts (typst-codegen.test.ts).
#let is-blank(v) = v == none or v == "" or v == []

// One accent + ink + one muted grey (cv-typography-standard rule 6). This template
// draws NO rules at all - the terraced indents are the structure - so the accent
// appears only as the section labels. Dates and every other secondary line are muted.
#let ink = rgb("#1f2430")
#let muted = rgb("#6b7280")

// Fixed size scale (standard rule 2), defined ONCE and expressed as em multiples of
// the resolved body size B - the `font-size` parameter, which the user can move between
// 9pt and 13pt. Absolute pt would detach the scale from that knob: at 9pt a 10pt label
// ends up LARGER than the body and meta stops being meta, and at 13pt the subhead falls
// below the body. em keeps every ratio intact at both ends of the range.
#let display-size = 2.35em // ~2.35 x B - the name
#let subhead-size = 1.18em // ~1.18 x B - the profession line
#let label-size = 0.9em // 0.9 x B - section labels (the one uppercase + tracked role)
#let meta-size = 0.82em // 0.82 x B - dates, location, contact, level, fine print

// The terrace: one step. Section labels and the name hang a step INTO the left
// margin, entry headings sit on the text edge, and entry bodies step in again, so
// the page has three left edges and needs no rules to show its hierarchy.
#let STEP = 16pt

// Document shell + masthead. EB Garamond is the single family (standard rule 1):
// regular + semibold + bold.
#let terrace-cv(
  author: "",
  profession: "",
  contact: "",
  font: "EB Garamond",
  font-size: 11pt,
  paper: "a4",
  margin: 2.2cm,
  leading: 0.68em,
  lang: "en",
  accent-color: "#0f5a46",
  body,
) = {
  let author-str = if type(author) == str { author } else { "CV" }
  set document(author: author-str, title: author-str + " - CV")

  set text(font: font, size: font-size, fill: ink, lang: lang, ligatures: true, hyphenate: false)
  set page(margin: margin, paper: paper)
  set par(justify: false, leading: leading)

  show link: set text(fill: muted)

  // Masthead: the name hangs on the outer terrace with the label column; the role and
  // contact sit on the text edge, so the header states the page's three left edges.
  let has-name = not is-blank(author)
  let has-prof = not is-blank(profession)
  let has-contact = not is-blank(contact)
  if has-name or has-prof or has-contact {
    block(above: 0pt, below: 1.5em, breakable: false, {
      set block(spacing: 0pt)
      set par(spacing: 0pt, leading: 0.38em, justify: false)
      if has-name {
        pad(left: -STEP, text(size: display-size, weight: "bold", fill: ink, author))
      }
      if has-prof {
        if has-name { v(headline-gap(display-size * 0.38)) }
        text(size: subhead-size, weight: "regular", fill: muted, profession)
      }
      if has-contact {
        if has-name or has-prof { v(subhead-size * 0.7) }
        text(size: meta-size, fill: muted, contact)
      }
    })
  }

  body
}

// Section label: accent UPPERCASE + tracking, hanging one step into the left margin.
// No rule anywhere in this template. `gap` is the section separation resolved from
// the sectionSpacing knob (see the adapter), so it scales with the entry gaps.
#let cv-section(title, accent-color: "#0f5a46", gap: 1.7em) = {
  block(above: gap, below: 1em, sticky: true, {
    set block(spacing: 0pt)
    set par(spacing: 0pt, justify: false)
    pad(left: -STEP, text(size: label-size, weight: "semibold", fill: rgb(accent-color), upper(title)))
  })
}

// Entry: heading on the text edge with the dates flush right; the body steps in by
// one terrace so the description reads as subordinate to its heading.
#let terrace-entry(title: "", subtitle: "", dates: "", location: "", meta: "", body) = {
  // ATOMIC on purpose. Letting an entry break fills the page foot, but it also lets a
  // single trailing bullet start the next page, which the review bar treats as an
  // automatic fail; measured over the fixture matrix the orphans cost more than the
  // whitespace. Same choice as every other template in the fleet.
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
          if not is-blank(subtitle) { linebreak(); text(fill: muted, subtitle) }
        },
        if not is-blank(dates) { text(size: meta-size, fill: muted, dates) } else { [] },
      )
      if not is-blank(location) {
        v(0.68em)
        text(size: meta-size, fill: muted, location)
      }
      if not is-blank(meta) {
        v(0.68em)
        text(size: meta-size, fill: muted, meta)
      }
    })
    // Wider than the 3pt above the location line, so the location binds to its title
    // instead of floating midway between the title and the first bullet.
    if not is-blank(body) {
      v(0.85em)
      pad(left: STEP, block(spacing: 0pt, { set text(fill: ink); body }))
    }
  })
}

// Language row: language left, level muted right.
#let terrace-language(language: "", level: "") = {
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

#show: terrace-cv.with(
  author: r.name,
  profession: headline,
  contact: contact-line,
  accent-color: "#0f5a46",
  font: "EB Garamond",
  font-size: lv("fontSize", 11) * 1pt * scale,
  paper: "a4",
  margin: lv("marginX", 2.2) * 1cm,
  leading: lv("lineSpacing", 0.68) * 1em,
  lang: lang,
)
#show: apply-spacing
#flat-body(section-gap(cv-section), terrace-entry)
