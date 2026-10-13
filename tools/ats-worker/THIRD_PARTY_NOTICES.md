# Third-party notices

## ATS Screener — scoring prompt

The prompt in `src/main/resources/prompts/` (`full-score.md` and the two `full-score.*.properties` files that
hold the wording that differs between scoring against a vacancy and scoring a resume alone) is taken from
[ATS Screener](https://github.com/sunnypatell/ats-screener), file `src/lib/engine/llm/prompts.ts`
(`buildFullScoringPrompt`). The text is unchanged except that its template placeholders were turned into
`{{...}}` markers filled by `ScoringPrompt.java`. ATS Screener was once included in this repository as a submodule; it is no longer used.

ATS Screener is released under the MIT License:

```
MIT License

Copyright (c) 2026 Sunny Patel

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
