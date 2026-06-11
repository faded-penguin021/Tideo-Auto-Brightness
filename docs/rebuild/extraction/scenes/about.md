# Scene: AAB About

- XML line range: **L799–1037** (`<Scene sr="sceneAAB About">`)
- Scene geom: 1440 x 2944 (portrait)
- Type: single full-screen **WebElement** rendering static HTML (About & License page) + a close button
- Target M3 screen: **About+Guide+Onboarding** (AboutScreen)

## Element count by type (top-level Scene children = 3)

| Type | Count |
|------|-------|
| WebElement | 1 |
| ButtonElement | 1 |
| PropertiesElement | 1 |
| **Total** | **3** |

(Each WebElement carries one nested `background` RectElement — intrinsic styling, not counted. The raw grep includes it.)

## Elements (in document order)

| # | name (sr) | type | bound variable | value range / options | handler task(s) | purpose |
|---|-----------|------|----------------|-----------------------|-----------------|---------|
| elements0 | (web, arg0="About") | WebElement | HTML references `%AAB_Version` | — | LinkClickFilter urlMatch="stop" (stopEvent) | Full-screen static "About & License" HTML page (banner + MIT license box). Black (#FF000000) background. |
| elements1 | Button1 | ButtonElement | — | icon `mw_navigation_close` (geom 1282,0,157,157) | clickTask=**685** | Top-right close (X) button → closes the About scene |
| props | props | PropertiesElement | — | title "AAB About", bg #FF000000 | keyTask=**596** | Scene properties; key/back handler task 596 |

## Static TEXT content (port to AboutScreen)

Banner: **Advanced Auto Brightness**

Heading: **About & License**
Subheading: **Version %AAB_Version** (bind to app version string)

Body paragraphs:
- "This project is a complete, native replacement for Android's stock auto-brightness system, built entirely within Tasker with **no plugins required**."
- "It was created by **/u/v_uurtjevragen** (link: https://www.reddit.com/user/v_uurtjevragen) to provide a more intelligent, responsive, and customizable brightness experience."

**Acknowledgments**
- "This project wouldn't be possible without the incredible power and flexibility of **Tasker**, developed by João Dias."
- "The awesome graphs you see in the settings panels are powered by the **Chart.js** library (link: https://www.chartjs.org/)." *(NOTE: Chart.js is removed in the Kotlin rebuild — drop this acknowledgment or reword.)*

**MIT License**
> Copyright (c) 2025 /u/v_uurtjevragen
>
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
>
> The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
>
> THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Theme colors: bg #333333, banner #007C63, accent headings #007C63/#00A986, links #00C79E, strong #FFC107, license box #383838.

## Disposition

| Element | Disposition |
|---------|-------------|
| elements0 (About web page) | about — kept-as About+Guide+Onboarding |
| elements1 (close button) | about — dropped(M3 nav back replaces in-scene close button) |
| props (scene properties / back task 596) | about — dropped(handled by Compose navigation) |
