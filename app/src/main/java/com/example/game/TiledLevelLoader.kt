package com.example.game

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Editable slice of a Tiled map. This is a prototype format boundary, not yet the
 * runtime source of truth; GameLevelFactory continues to build the live full level.
 */
internal data class TiledLevelSection(
    val platforms: List<Platform>,
    val hazards: List<Hazard>,
    val enemies: List<EnemyEntity>,
    val breakables: List<BreakableUrn>
)

/**
 * Reads Tiled's finite orthogonal TMX object layers and converts their pixel-space
 * rectangles into the game's existing world models. No rendering or physics rules
 * are duplicated here.
 */
internal object TiledLevelLoader {
    fun load(input: InputStream): TiledLevelSection {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder().parse(input)
        val map = document.documentElement
        require(map.tagName == "map") { "Expected a Tiled TMX <map> root element" }
        require(map.getAttribute("orientation") == "orthogonal") {
            "Only orthogonal Tiled maps are supported"
        }

        val platforms = objectsInLayer(document, "Platforms").map { obj ->
            Platform(
                x = obj.floatAttribute("x"),
                y = obj.floatAttribute("y"),
                w = obj.floatAttribute("width"),
                h = obj.floatAttribute("height"),
                style = enumProperty(obj, "style", PlatformStyle::valueOf)
            )
        }
        val hazards = objectsInLayer(document, "Hazards").map { obj ->
            Hazard(
                x = obj.floatAttribute("x"),
                y = obj.floatAttribute("y"),
                w = obj.floatAttribute("width"),
                h = obj.floatAttribute("height"),
                type = enumProperty(obj, "type", HazardType::valueOf)
            )
        }
        val enemies = objectsInLayer(document, "Enemies").map { obj ->
            EnemyEntity(
                x = obj.floatAttribute("x"),
                y = obj.floatAttribute("y"),
                type = enumProperty(obj, "archetype", EnemyArchetype::valueOf),
                patrolRange = obj.optionalProperty("patrolRange")?.toFloatOrNull() ?: 160f
            )
        }
        val breakables = objectsInLayer(document, "Breakables").map { obj ->
            BreakableUrn(
                x = obj.floatAttribute("x"),
                y = obj.floatAttribute("y"),
                w = obj.floatAttribute("width"),
                h = obj.floatAttribute("height"),
                dropType = obj.requiredProperty("dropType")
            )
        }

        return TiledLevelSection(
            platforms = platforms,
            hazards = hazards,
            enemies = enemies,
            breakables = breakables
        )
    }

    private fun objectsInLayer(
        document: org.w3c.dom.Document,
        layerName: String
    ): List<Element> {
        val groups = document.getElementsByTagName("objectgroup")
        val group = (0 until groups.length)
            .mapNotNull { groups.item(it) as? Element }
            .firstOrNull { it.getAttribute("name") == layerName }
            ?: return emptyList()

        val objects = group.getElementsByTagName("object")
        return (0 until objects.length).mapNotNull { objects.item(it) as? Element }
    }

    private fun Element.floatAttribute(name: String): Float {
        require(hasAttribute(name)) { "Tiled object is missing '$name'" }
        val value = getAttribute(name).toFloatOrNull()
            ?: throw IllegalArgumentException("Invalid '$name' value: ${getAttribute(name)}")
        require(value.isFinite()) { "'$name' must be finite" }
        return value
    }

    private fun Element.optionalProperty(name: String): String? {
        val propertyLists = getElementsByTagName("property")
        for (index in 0 until propertyLists.length) {
            val property = propertyLists.item(index) as? Element ?: continue
            if (property.getAttribute("name") == name) {
                return if (property.hasAttribute("value")) {
                    property.getAttribute("value")
                } else {
                    property.textContent.trim()
                }
            }
        }
        return null
    }

    private fun Element.requiredProperty(name: String): String =
        optionalProperty(name)?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Tiled object is missing property '$name'")

    private fun <T> enumProperty(
        obj: Element,
        name: String,
        parse: (String) -> T
    ): T {
        val value = obj.requiredProperty(name)
        return try {
            parse(value)
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Invalid '$name' value '$value' on Tiled object '${obj.getAttribute("name")}'",
                exception
            )
        }
    }
}
