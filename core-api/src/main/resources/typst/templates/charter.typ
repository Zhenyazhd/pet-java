// Charter CV, a Typst resume template.
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
// Edit in a browser: https://www.jobsprout.ai/resume-templates/charter
#set page(fill: rgb("#ffffff"))

// Shared predicate for values that render nothing. Apply BEFORE wrapping a
// value in strong()/emph()/text(): styled empty content is not blank.
// Byte-synced with TYPST_IS_BLANK in utils/typst-blocks.ts (typst-codegen.test.ts).
#let is-blank(v) = v == none or v == "" or v == []

// One accent + ink + one muted grey (cv-typography-standard rule 6). The accent is
// drawn only as the two rule weights (the heavy masthead rule and the lighter section
// rules); the labels and entry leads are ink, the second line and meta are muted.
#let ink = rgb("#1f2430")
#let muted = rgb("#6b7280")

// Fixed size scale (standard rule 2), defined ONCE and expressed as em multiples of
// the resolved body size B - the `font-size` parameter, which the user can move between
// 9pt and 13pt. Absolute pt would detach the scale from that knob: at 9pt a 10pt label
// ends up LARGER than the body and meta stops being meta, and at 13pt the subhead falls
// below the body. em keeps every ratio intact at both ends of the range.
// A deliberate FOUR-step scale: this template shows no role line under the name
// (showRole is false), so the subhead step has no element and is not declared.
#let display-size = 2.25em // ~2.25 x B - the name
#let label-size = 0.9em // 0.9 x B - section labels (the one uppercase + tracked role)
#let meta-size = 0.82em // 0.82 x B - dates, location, contact, level, fine print

// Document shell + masthead. Two signatures, both from the consulting/business-school
// convention: RULES carry the structure (a heavy rule closes the masthead, a lighter
// one every section label), and every entry is ORGANISATION-FIRST - the employer leads
// in bold with the location flush right, the role sits beneath it in italic with the
// dates flush right. There is deliberately no role line under the name: the first
// entry states it. Source Sans 3 is the single family (standard rule 1).
#let charter-cv(
  author: "",
  profession: "",
  contact: "",
  font: "Source Sans 3",
  font-size: 10.5pt,
  paper: "a4",
  margin: 1.7cm,
  leading: 0.63em,
  lang: "en",
  accent-color: "#14304f",
  body,
) = {
  let author-str = if type(author) == str { author } else { "CV" }
  set document(author: author-str, title: author-str + " - CV")

  set text(font: font, size: font-size, fill: ink, lang: lang, ligatures: false, hyphenate: false)
  set page(margin: margin, paper: paper)
  set par(justify: false, leading: leading)

  show link: set text(fill: muted)

  let has-name = not is-blank(author)
  let has-contact = not is-blank(contact)
  let has-prof = not is-blank(profession)
  if has-name or has-contact {
    block(above: 0pt, below: 1.15em, breakable: false, {
      set block(spacing: 0pt)
      set par(spacing: 0pt, leading: 0.4em, justify: false)
      if has-name { text(size: display-size, weight: "bold", fill: ink, author) }
      if has-prof {
        if has-name { v(headline-gap(display-size * 0.3)) }
        text(size: 1.3em, fill: ink, profession)
      }
      if has-contact {
        if has-name or has-prof { v(display-size * 0.38) }
        text(size: meta-size, fill: muted, contact)
      }
      // The heavy rule CLOSES the name; a contact-only document gets no floating rule.
      if has-name {
        v(8pt)
        line(length: 100%, stroke: 2pt + rgb(accent-color))
      }
    })
  }

  // The first section brings its own gap above it; pull it in so the rule and the first heading are not far apart.
  v(-0.8em)
  body
}

// Section label: ink UPPERCASE + tracking over a lighter accent rule, the second of
// the template's two rule weights. `below: 0.9em` gives the first entry natural air.
#let cv-section(title, accent-color: "#14304f", gap: 1.7em) = {
  block(above: gap, below: 1em, sticky: true, {
    set block(spacing: 0pt)
    set par(spacing: 0pt, justify: false)
    text(size: label-size, weight: "bold", tracking: 1pt, fill: ink, upper(title))
    v(4pt)
    line(length: 100%, stroke: 0.9pt + rgb(accent-color))
  })
}

// Entry, organisation-first: line one is the lead (the employer, school or issuer) in
// bold with the location flush right; line two is the role in italic with the dates
// flush right. When a section carries no location (certifications, awards) the dates
// move up to line one so no cell is left hanging empty.
#let charter-entry(title: "", subtitle: "", dates: "", location: "", meta: "", body) = {
  // Organisation-first, but never at the cost of an empty line: with no organisation the
  // ROLE is promoted to the lead so the entry can never render a blank left cell with a
  // date floating opposite it.
  let lead = if not is-blank(title) { title } else { subtitle }
  let follow = if not is-blank(title) { subtitle } else { "" }
  let has-location = not is-blank(location)
  let has-dates = not is-blank(dates)
  let has-subtitle = not is-blank(follow)
  // Line two exists when there is a subtitle, or when a location pushed the dates down.
  let second-left = if has-subtitle { text(style: "italic", fill: muted, follow) } else { [] }
  let second-right = if has-location and has-dates { text(size: meta-size, fill: muted, dates) } else { [] }
  let first-right = if has-location {
    text(size: meta-size, fill: muted, location)
  } else if has-dates {
    text(size: meta-size, fill: muted, dates)
  } else { [] }

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
        if not is-blank(lead) { text(weight: "bold", fill: ink, lead) } else { [] },
        first-right,
      )
      if has-subtitle or not is-blank(second-right) {
        v(0.42em)
        grid(
          columns: (1fr, auto),
          align: (left + top, right + top),
          column-gutter: 0.75em,
          second-left,
          second-right,
        )
      }
      if not is-blank(meta) {
        v(0.68em)
        text(size: meta-size, fill: muted, meta)
      }
    })
    if not is-blank(body) {
      v(0.85em)
      block(spacing: 0pt, { set text(fill: ink); body })
    }
  })
}

// Language row: language left, level muted right.
#let charter-language(language: "", level: "") = {
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

#show: charter-cv.with(
  author: r.name,
  accent-color: "#14304f",
  font: "Source Sans 3",
  font-size: lv("fontSize", 10.5) * 1pt * scale,
  paper: "a4",
  margin: lv("marginX", 1.7) * 1cm,
  leading: lv("lineSpacing", 0.63) * 1em,
  lang: lang,
  profession: headline,
  contact: contact-line,
)
#show: apply-spacing
#flat-body(section-gap(cv-section), charter-entry)
