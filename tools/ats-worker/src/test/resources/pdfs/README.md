# Fixture PDFs

PDFs typeset by tectonic with the production template, so the checks are tested on what production produces (the unit
tests' PDFBox-made PDFs have one `Tj` per line and hid several wrong results).

| File | What it is | Expected text |
|---|---|---|
| `six-en.pdf` | `ResumeLatexRenderer` output, English, six roles | `six-en.txt` (`ResumePlainTextRenderer` output) |
| `six-fr.pdf` | the same with French headings | `six-fr.txt` |
| `multicol-en.pdf` | the same content in `multicols{2}` with LaTeX's default 10 pt gutter | `six-en.txt` |
| `sidebar-en.pdf` | Education, Achievements and Skills in a 27% left minipage, the rest in a 68% right one | `six-en.txt` |
| `skills-table-en.pdf` | single column; the skills are a one-row, three-cell `tabular` | `six-en.txt` |

`six-*.tex` were written by a throw-away core-api test that rendered a `ResumeDocument` with the six roles through
`ResumeLatexRenderer` and `ResumePlainTextRenderer`; the other three `.tex` files are `six-en.tex` with the body
re-arranged. Rebuild with `./build.sh`.
