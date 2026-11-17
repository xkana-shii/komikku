package eu.kanade.tachiyomi.network.interceptor

// KNS -->
/**
 * Page script that finds the Cloudflare verification widget, hides everything else on the page and reports where the
 * widget is (`widget:left,top,width,height`) so the dialog can show only that box.
 *
 * The widget is located by, in order: a challenge iframe (also inside open shadow roots), the known wrappers, and the
 * container of the hidden response input / `data-sitekey` element (which covers a widget living in a closed shadow
 * root or a cross-origin iframe, whose host element is still in the light DOM). The rest of the page is hidden with
 * `display: none` on the siblings of the widget's ancestors, never removed, so the challenge's own scripts keep
 * working. If nothing is found nothing is hidden and nothing is reported, and the dialog shows the full page.
 */
internal object ChallengeWidgetScript {
    val SOURCE = """
        ;(() => {
            const widgetSelectors = [
                'iframe[src*="challenges.cloudflare.com"]',
                '.cf-turnstile', '#cf-turnstile', '[id^="cf-chl-widget"]', '#turnstile-wrapper',
            ]
            const containerSignals = ['input[name="cf-turnstile-response"]', '[data-sitekey]']
            const MIN = 20

            const deepQuery = (root, selector) => {
                const found = root.querySelector(selector)
                if (found) return found
                for (const el of root.querySelectorAll('*')) {
                    if (el.shadowRoot) {
                        const inner = deepQuery(el.shadowRoot, selector)
                        if (inner) return inner
                    }
                }
                return null
            }

            const big = (el) => {
                const r = el.getBoundingClientRect()
                return r.width >= MIN && r.height >= MIN
            }

            const findWidget = () => {
                for (const selector of widgetSelectors) {
                    const el = deepQuery(document, selector)
                    if (el && big(el)) return el
                }
                for (const selector of containerSignals) {
                    const el = deepQuery(document, selector)
                    if (!el) continue
                    // A hidden input is itself invisible, its container is the widget
                    const target = el.tagName === 'INPUT' ? el.parentElement : el
                    if (target && big(target)) return target
                }
                return null
            }

            const parentOf = (n) => n.parentElement || (n.getRootNode && n.getRootNode().host) || null
            const hide = (el) => el.style.setProperty('display', 'none', 'important')
            const flatten = (el) => {
                for (const p of ['margin', 'padding', 'border']) el.style.setProperty(p, '0', 'important')
                for (const p of ['min-height', 'height', 'max-width']) {
                    el.style.setProperty(p, p === 'max-width' ? 'none' : 'auto', 'important')
                }
                el.style.setProperty('text-align', 'center', 'important')
            }

            const isolate = (widget) => {
                const keep = new Set()
                for (let n = widget; n; n = parentOf(n)) keep.add(n)
                for (const n of keep) {
                    const parent = n.parentNode
                    if (parent && parent.children) {
                        for (const sibling of parent.children) {
                            if (!keep.has(sibling) && !/^(SCRIPT|STYLE|LINK|META|TITLE)${'$'}/.test(sibling.tagName)) {
                                hide(sibling)
                            }
                        }
                    }
                    if (n !== widget && n !== document.documentElement) flatten(n)
                }
                document.documentElement.style.setProperty('overflow-x', 'hidden', 'important')
                if (document.body) {
                    document.body.style.setProperty('display', 'flex', 'important')
                    document.body.style.setProperty('flex-direction', 'column', 'important')
                    document.body.style.setProperty('align-items', 'center', 'important')
                }
            }

            let last = ''
            let timer = 0
            const run = () => {
                timer = 0
                observer.disconnect()
                try {
                    const widget = findWidget()
                    if (widget) {
                        isolate(widget)
                        const r = widget.getBoundingClientRect()
                        const text = 'widget:' + [r.left + scrollX, r.top + scrollY, r.width, r.height]
                            .map((v) => Math.round(v)).join(',')
                        if (text !== last) {
                            last = text
                            mihon?.postMessage(text)
                        }
                    }
                } finally {
                    observer.observe(document, { subtree: true, childList: true, attributes: true })
                }
            }
            const schedule = () => {
                if (!timer) timer = setTimeout(run, 50)
            }
            const observer = new MutationObserver(schedule)
            observer.observe(document, { subtree: true, childList: true, attributes: true })
            addEventListener('resize', schedule)
            addEventListener('load', schedule)
        })()
    """.trimIndent()
}
// KNS <--
