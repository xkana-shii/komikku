# KMK -->
# Ported from mihon 6dfa10ffac ("Ensure tachiyomix public API is not removed by R8", fixes #4027).
# Replaces the pre-fix broad rules (`model.**` / `online.**` / `source.** extends Source`), which
# did not match the `*$DefaultImpls` classes extensions link against.
# KMK <--
# Types extensions implement, extend or construct
-keep class eu.kanade.tachiyomi.source.Source { public protected *; }
-keep class eu.kanade.tachiyomi.source.Source$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.CatalogueSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.CatalogueSource$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.ConfigurableSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.SourceFactory { public protected *; }
-keep class eu.kanade.tachiyomi.source.UnmeteredSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.Filter { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.Filter$* { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.FilterList { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.MangasPage { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.Page { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SChapter { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SChapter$Companion { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SManga { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SManga$Companion { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SMangaUpdate { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.UpdateStrategy { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.HttpSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.ParsedHttpSource { public protected *; }

# KMK -->
# Kotlin compiles an interface's default-method bodies into a nested `*$DefaultImpls` class, which
# is what an extension links against when it implements an interface but doesn't override every
# default. mihon's list above keeps the interfaces themselves (and only the `Source`/`CatalogueSource`
# `*$DefaultImpls` classes), so R8 may still strip the `*$DefaultImpls` bodies the extension dex
# resolves at runtime. Keep every extension-facing interface together with its default-method holder,
# plus the SY/Komikku-only types, so R8 cannot drop API extensions link against. Safe superset.
-keep class eu.kanade.tachiyomi.source.ConfigurableSource$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.PagePreviewSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.PagePreviewSource$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.PagePreviewPage { public protected *; }
-keep class eu.kanade.tachiyomi.source.PagePreviewInfo { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SManga$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.SChapter$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.model.MetadataMangasPage { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.FollowsSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.FollowsSource$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.LoginSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.LoginSource$* { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.MetadataSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.MetadataSource$* { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.NamespaceSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.NamespaceSource$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.RandomMangaSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.RandomMangaSource$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.ResolvableSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.ResolvableSource$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.UriType { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.UriType$* { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.UrlImportableSource { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.UrlImportableSource$DefaultImpls { public protected *; }
-keep class eu.kanade.tachiyomi.source.online.all.EhBasedSource { public protected *; }

# Top-level function holders extensions call into directly (SY/Komikku additions on top of mihon).
-keep,allowoptimization class eu.kanade.tachiyomi.source.ConfigurableSourceKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.source.model.SMangaKt { public protected *; }

# SY/Komikku delegated-source base types, referenced/instantiated via reflection by the app.
-keep class exh.source.** { public protected *; }
# KMK <--

# Final classes and top-level functions extensions only call into
-keep,allowoptimization class eu.kanade.tachiyomi.AppInfo { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.HttpException { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.JavaScriptEngine { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.NetworkHelper { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.OkHttpExtensionsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.RequestsKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.RateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.network.interceptor.SpecificHostRateLimitInterceptorKt { public protected *; }
-keep,allowoptimization class eu.kanade.tachiyomi.util.JsoupExtensionsKt { public protected *; }

-keep class exh.metadata.** { public protected *; }
