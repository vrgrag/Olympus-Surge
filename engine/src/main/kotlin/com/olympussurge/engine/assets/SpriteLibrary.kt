package com.olympussurge.engine.assets

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.utils.JsonReader

/**
 * Loads sprites described by `assets/sprites.json` and hands out regions by
 * name.
 *
 * Loading is incremental: [update] does a slice of work per frame and reports
 * real progress, which is what the loading screen shows. Nothing here is
 * simulated or padded.
 */
class SpriteLibrary {

    private val manager = AssetManager()
    private val entries = ArrayList<Entry>()
    private val regions = HashMap<String, TextureRegion>()
    private val anchors = HashMap<String, Anchor>()

    class Anchor(val x: Float, val y: Float)

    private class Entry(val name: String, val path: String, val anchorX: Float, val anchorY: Float)

    var totalCount = 0
        private set

    /** Queues every sprite in the manifest. Cheap: no pixels are read yet. */
    fun enqueueAll(filter: (category: String) -> Boolean = { true }) {
        val root = JsonReader().parse(Gdx.files.internal("sprites.json"))
        for (i in 0 until root.size) {
            val node = root[i]
            if (!filter(node.getString("category"))) continue
            val entry = Entry(
                name = node.getString("name"),
                path = node.getString("file"),
                anchorX = node.getFloat("anchorX"),
                anchorY = node.getFloat("anchorY"),
            )
            entries += entry
            manager.load(entry.path, Texture::class.java)
        }
        totalCount = entries.size
    }

    /** Returns true once every queued sprite is resident on the GPU. */
    fun update(millis: Int = 8): Boolean = manager.update(millis)

    /** Real fraction of queued assets that finished loading, 0..1. */
    fun progress(): Float = if (totalCount == 0) 1f else manager.progress

    /** Publishes loaded textures as regions; call once after [update] is done. */
    fun finish() {
        manager.finishLoading()
        for (entry in entries) {
            val texture = manager.get(entry.path, Texture::class.java)
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
            regions[entry.name] = TextureRegion(texture)
            anchors[entry.name] = Anchor(entry.anchorX, entry.anchorY)
        }
    }

    operator fun get(name: String): TextureRegion =
        regions[name] ?: error("Sprite '$name' is not loaded")

    fun anchorOf(name: String): Anchor = anchors[name] ?: Anchor(0.5f, 1f)

    fun dispose() {
        regions.clear()
        anchors.clear()
        manager.dispose()
    }
}
