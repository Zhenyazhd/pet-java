// Quartz CV, a Typst resume template.
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
// Edit in a browser: https://www.jobsprout.ai/resume-templates/quartz
#set page(fill: rgb("#ffffff"))

// Shared predicate for values that render nothing. Apply BEFORE wrapping a
// value in strong()/emph()/text(): styled empty content is not blank.
// Byte-synced with TYPST_IS_BLANK in utils/typst-blocks.ts (typst-codegen.test.ts).
#let is-blank(v) = v == none or v == "" or v == []

// One accent + ink + one muted grey (cv-typography-standard rule 6). The accent is used
// as ONE tasteful block: a SOFT-TINT full-height left rail (a lightened wash of the
// accent). Because the tint is light, the rail keeps DARK text (ink/muted) - legible on
// the wash, so the global AI-diff colours also read fine and need no shadowing. The
// accent also tints the section labels/rules. Default accent a muted slate-green.
#let ink = rgb("#1f2430")
#let muted = rgb("#6b7280")

#let PANEL-FRAC = 33%

// Fixed size scale from body B = 10pt (standard rule 2), defined ONCE.
#let display-size = 18pt // ~1.8 x B - the name (in the narrow rail)
#let subhead-size = 11pt // ~1.1 x B - the profession line
#let body-size = 10pt // B
#let label-size = 9pt // ~0.9 x B - section labels (uppercase + tracked)
#let meta-size = 8.2pt // ~0.82 x B - dates, location, contact, level, fine print

// Document shell: set-rules + PDF metadata + the tint rail. The page background paints a
// light wash of the accent down the left PANEL-FRAC of EVERY page (so a long CV keeps the
// rail). Page left/right margins are 0 so the rail is edge-to-edge; top/bottom margins
// give the flowing main column its per-page margins. Raleway is the single family.
#let quartz-cv(
  author: "",
  font: "Raleway",
  font-size: 10pt,
  paper: "a4",
  margin: 1.4cm,
  leading: 0.62em,
  lang: "en",
  accent-color: "#3f5450",
  body,
) = {
  let author-str = if type(author) == str { author } else { "CV" }
  set document(author: author-str, title: author-str + " - CV")

  set text(font: font, size: font-size, fill: ink, lang: lang, ligatures: false, hyphenate: false)
  set page(
    paper: paper,
    margin: (top: margin, bottom: margin, left: 0pt, right: 0pt),
    background: place(top + left, rect(width: PANEL-FRAC, height: 100%, fill: rgb(accent-color).lighten(80%))),
  )
  set par(justify: false, leading: leading)

  show link: set text(fill: muted)

  body
}

// Two-column layout as a BREAKABLE grid, so a long aside flows onto page 2+ alongside
// the main column. It was previously `place`d at the top, contributing zero flow height:
// that let the main column alone drive pagination, but an aside taller than the page then
// kept drawing past the paper edge, overlapping its own entries and losing text (a
// 45-language sidebar reached 2044pt on an 842pt page). The rail fill spans every page via
// the page background, so it still looks continuous when the aside runs short.
// Horizontal padding sits inside each column; the vertical margins come from the page.
#let cv-layout(main: [], aside: [], hpad: 1.4cm, gap: 22pt) = {
  grid(
    columns: (PANEL-FRAC, 1fr),
    column-gutter: gap,
    align: (left + top, left + top),
    block(inset: (left: hpad, right: 12pt), {
      // No mid-word hyphenation in the narrow rail: a long name or skill wraps at a space.
      set text(hyphenate: false)
      aside
    }),
    block(inset: (right: hpad), main),
  )
}

// Rail masthead: an optional circle-clipped photo above the name (bold ink)
// over the profession (accent). photo is a VFS path string; none renders
// nothing and reserves no space.
#let masthead(author: "", profession: "", accent-color: "#3f5450", photo: none, photo-radius: 50%, photo-size: 2.6cm) = {
  block(above: 0pt, below: 1.1em, breakable: false, {
    set block(spacing: 0pt)
    set par(spacing: 0pt, leading: 0.4em, justify: false)
    if photo != none {
      box(
        clip: true,
        radius: photo-radius,
        width: photo-size,
        height: photo-size,
        stroke: 0.6pt + rgb(accent-color),
        image(photo, width: photo-size, height: photo-size, fit: "cover"),
      )
      v(10pt)
    }
    text(size: display-size, weight: "bold", fill: ink, author)
    if profession != "" {
      v(3pt)
      text(size: subhead-size, weight: "regular", fill: rgb(accent-color), profession)
    }
  })
}

// Rail section label: accent UPPERCASE + tracking over a thin accent rule.
#let panel-section(title, accent-color: "#3f5450") = {
  block(above: 1.4em, below: 0.9em, sticky: true, {
    set block(spacing: 0pt)
    set par(spacing: 0pt, justify: false)
    text(size: label-size, weight: "semibold", tracking: 0.8pt, fill: rgb(accent-color), upper(title))
    v(3pt)
    line(length: 100%, stroke: 0.5pt + rgb(accent-color).lighten(35%))
  })
}

// Rail contact: a CONTACT label then one muted line per item (email, phone, location,
// social), a quiet legible stack.
#let panel-contact(items, accent-color: "#3f5450") = {
  panel-section("Contact", accent-color: accent-color)
  set block(spacing: 0pt)
  set par(spacing: 0pt)
  for item in items {
    block(above: 0pt, below: 4pt, breakable: false, {
      set text(size: meta-size, fill: muted)
      item
    })
  }
}

// Rail credential (certifications / awards): compact stacked - name (semibold ink), then
// issuer + date muted beneath, dot-separated.
#let quartz-rail-cred(name: "", issuer: "", date: "") = {
  block(breakable: false, {
    set block(spacing: 0pt)
    set par(spacing: 0pt, justify: false)
    set text(hyphenate: false)
    if not is-blank(name) { text(weight: "semibold", fill: ink, name) }
    let meta = (issuer, date).filter(v => not is-blank(v)).join(text(fill: muted)[ #sym.dot.c ])
    if not is-blank(meta) { v(2pt); text(size: meta-size, fill: muted, meta) }
  })
}

// Rail language: language over the proficiency level (muted) STACKED beneath.
#let quartz-language(language: "", level: "") = {
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

// Main section label: accent UPPERCASE + tracking over an accent rule.
#let main-section(title, accent-color: "#3f5450") = {
  block(above: 1.3em, below: 0.9em, sticky: true, {
    set block(spacing: 0pt)
    set par(spacing: 0pt, justify: false)
    text(size: label-size, weight: "semibold", tracking: 0.8pt, fill: rgb(accent-color), upper(title))
    v(3pt)
    line(length: 100%, stroke: 0.6pt + rgb(accent-color))
  })
}

// Main entry: semibold-ink title leads with a muted subtitle, dates right; location and
// an optional URL meta line below; then the body. No mid-word hyphenation on the title.
#let quartz-entry(title: "", subtitle: "", dates: "", location: "", meta: "", body) = {
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

// ---------------------------------------------------------------------------------------------------------------
// Adapted for this project: the sample document is replaced by the resume `r`, and the page settings of the
// editor (font size, line spacing, left/right margin) override the template's own values when they are set.

#show: quartz-cv.with(
  author: r.name,
  accent-color: "#3f5450",
  font: "Raleway",
  font-size: lv("fontSize", 10) * 1pt * scale,
  paper: "a4",
  margin: lv("marginX", 1.4) * 1cm,
  leading: lv("lineSpacing", 0.62) * 1em,
  lang: lang,
)
#cv-layout(
  main: [#main-body(main-section, quartz-entry)],
  aside: [#masthead(author: r.name, profession: r.headline, accent-color: "#3f5450")#panel-contact(contacts, accent-color: "#3f5450")#side-body(panel-section)],
)
