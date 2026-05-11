package com.nearlink.messenger.core.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 简易语音播放器。仅一个会话；按需扩展为 LRU 池。
 */
@Singleton
class AudioPlayer @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private var player: ExoPlayer? = null

    fun play(uri: String, onEnded: () -> Unit = {}) {
        stop()
        val p = ExoPlayer.Builder(ctx).build()
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        p.playWhenReady = true
        p.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_ENDED) {
                    onEnded()
                    stop()
                }
            }
        })
        player = p
    }

    fun stop() {
        player?.release()
        player = null
    }
}
