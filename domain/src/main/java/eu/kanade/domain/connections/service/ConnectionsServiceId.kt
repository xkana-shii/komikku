// KUN -->
package eu.kanade.domain.connections.service

/**
 * The one thing [ConnectionsPreferences] needs from a connection service to build its per-service
 * preference keys: the stable service id.
 *
 * It is a `domain` interface because [ConnectionsPreferences] lives in `:domain` (so the category-id
 * inventory `CategoryIdPreferences` can include `discordRPCIncognitoCategories`), and `:domain`
 * cannot see the app's abstract `ConnectionsService`, which implements it.
 */
interface ConnectionsServiceId {
    val id: Long
}
// KUN <--
