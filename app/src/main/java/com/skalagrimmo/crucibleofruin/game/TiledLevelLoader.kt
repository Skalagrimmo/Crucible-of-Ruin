package com.skalagrimmo.crucibleofruin.game

import org.w3c.dom.Element
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Editable slice of a Tiled map. Stage 10a (#69) extends the prototype format
 * with the features a live level needs — moving platforms, gate identity,
 * explicit collision overrides, and map-level world bounds — while
 * GameLevelFactory remains the runtime source of truth until the Stage 10d
 * switch.
 */
internal data class TiledLevelSection(
    val platforms: List<Platform>,
    val hazards: List<Hazard>,
    val enemies: List<EnemyEntity>,
    val breakables: List<BreakableUrn>,
    // Stage 10a (#69): world bounds from the map's levelWidth/levelHeight
    // properties. The defaults keep the stock corridor bounds for sections
    // that carry no map-level bounds yet.
    val width: Float = 6200f,
    val height: Float = 540f
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
            val style = enumProperty(obj, "style", PlatformStyle::valueOf)
            // Stage 10a (#69): an explicit collision property is the strongest
            // override — boss arena gates need OPEN — while the Stage 3 oneWay
            // property stays as the shorthand; without either, the style's
            // defaultCollision applies.
            val explicitCollision = obj.optionalProperty("collision")?.trim()?.takeIf { it.isNotEmpty() }
            Platform(
                x = obj.floatAttribute("x"),
                y = obj.floatAttribute("y"),
                w = obj.floatAttribute("width"),
                h = obj.floatAttribute("height"),
                style = style,
                collision = when {
                    explicitCollision != null ->
                        parseEnumValue(obj, "collision", explicitCollision, PlatformCollision::valueOf)
                    else -> when (obj.optionalProperty("oneWay")?.trim()?.lowercase()) {
                        "true" -> PlatformCollision.ONE_WAY
                        "false" -> PlatformCollision.SOLID
                        else -> style.defaultCollision
                    }
                },
                // Stage 10a (#69): a moving platform carries its ping-pong delta
                // and speed as properties; the object rectangle is the motion base.
                motion = obj.optionalMotion(),
                // Stage 10a (#69): sealed shortcut gates carry the trigger
                // identity that unlocks them.
                gateId = obj.optionalProperty("gateId")?.trim()?.takeIf { it.isNotEmpty() }
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
            breakables = breakables,
            // Stage 10a (#69): world bounds travel with the map properties.
            width = mapBounds(map, "levelWidth", 6200f),
            height = mapBounds(map, "levelHeight", 540f)
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
        require(hasAttribute(name)) { "Tiled object is missing '" + name + "'" }
        val value = getAttribute(name).toFloatOrNull()
            ?: throw IllegalArgumentException("Invalid '" + name + "' value: " + getAttribute(name))
        require(value.isFinite()) { "'" + name + "' must be finite" }
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
            ?: throw IllegalArgumentException("Tiled object is missing property '" + name + "'")

    private fun <T> enumProperty(
        obj: Element,
        name: String,
        parse: (String) -> T
    ): T = parseEnumValue(obj, name, obj.requiredProperty(name), parse)

    private fun <T> parseEnumValue(
        obj: Element,
        name: String,
        value: String,
        parse: (String) -> T
    ): T = try {
        parse(value)
    } catch (exception: IllegalArgumentException) {
        throw IllegalArgumentException(
            "Invalid '" + name + "' value '" + value + "' on Tiled object '" + obj.getAttribute("name") + "'",
            exception
        )
    }

    /**
     * Stage 10a (#69): a map-level property from the <map> element's own
     * <properties> block — object-layer properties are never consulted.
     */
    private fun mapProperty(map: Element, name: String): String? {
        val children = map.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.tagName != "properties") continue
            val properties = child.getElementsByTagName("property")
            for (propertyIndex in 0 until properties.length) {
                val property = properties.item(propertyIndex) as? Element ?: continue
                if (property.getAttribute("name") == name) {
                    return if (property.hasAttribute("value")) {
                        property.getAttribute("value")
                    } else {
                        property.textContent.trim()
                    }
                }
            }
        }
        return null
    }

    /** Stage 10a (#69): a positive finite map-level bound, or its default. */
    private fun mapBounds(map: Element, name: String, default: Float): Float {
        val raw = mapProperty(map, name) ?: return default
        val value = raw.toFloatOrNull()
            ?: throw IllegalArgumentException("Invalid '" + name + "' map property: " + raw)
        require(value.isFinite() && value > 0f) {
            "'" + name + "' must be a positive finite number"
        }
        return value
    }

    /**
     * Stage 10a (#69): motion properties build the deterministic Stage 6c
     * ping-pong PlatformMotion with the object rectangle as its base.
     * Declaring only part of the motionDx/motionDy/motionSpeed triple fails
     * fast instead of silently producing a static platform.
     */
    private fun Element.optionalMotion(): PlatformMotion? {
        val dxRaw = optionalProperty("motionDx")
        val dyRaw = optionalProperty("motionDy")
        val speedRaw = optionalProperty("motionSpeed")
        if (dxRaw == null && dyRaw == null && speedRaw == null) return null
        val objectName = getAttribute("name")
        require(dxRaw != null && dyRaw != null && speedRaw != null) {
            "Moving platform '" + objectName + "' must declare motionDx, motionDy and motionSpeed together"
        }
        val dx = requireFiniteFloat(dxRaw!!, "motionDx", objectName)
        val dy = requireFiniteFloat(dyRaw!!, "motionDy", objectName)
        val speed = requireFiniteFloat(speedRaw!!, "motionSpeed", objectName)
        require(speed > 0f) {
            "'motionSpeed' must be positive on Tiled object '" + objectName + "'"
        }
        val startPhase = optionalProperty("motionStartPhase")?.let { raw ->
            val phase = requireFiniteFloat(raw, "motionStartPhase", objectName)
            require(phase in 0f..1f) {
                "'motionStartPhase' must be within [0, 1] on Tiled object '" + objectName + "'"
            }
            phase
        } ?: 0f
        return PlatformMotion(
            baseX = floatAttribute("x"),
            baseY = floatAttribute("y"),
            dx = dx,
            dy = dy,
            speed = speed,
            startPhase = startPhase
        )
    }

    private fun requireFiniteFloat(raw: String, name: String, objectName: String): Float {
        val value = raw.toFloatOrNull()
            ?: throw IllegalArgumentException(
                "Invalid '" + name + "' value '" + raw + "' on Tiled object '" + objectName + "'"
            )
        require(value.isFinite()) {
            "'" + name + "' must be finite on Tiled object '" + objectName + "'"
        }
        return value
    }
}