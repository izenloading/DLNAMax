package com.dlnamax.tvreceiver.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class Media3DlnaPlayer(
    context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()

    @Volatile
    private var state: PlayerSnapshot = PlayerSnapshot()

    init {
        runOnPlayerThread {
            exoPlayer.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updatePlaybackSnapshot(playbackState.toDlnaPlaybackState())
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlaybackSnapshot(
                            if (isPlaying) {
                                PlaybackState.PLAYING
                            } else {
                                exoPlayer.playbackState.toDlnaPlaybackState()
                            },
                        )
                    }
                },
            )
        }
    }

    fun setMediaUri(uri: String) {
        state = state.copy(currentUri = uri, playbackState = PlaybackState.STOPPED)
        runOnPlayerThread {
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            Log.i(TAG, "Media URI set: $uri")
        }
    }

    fun play() {
        val currentUri = state.currentUri
        if (currentUri.isBlank()) {
            Log.w(TAG, "Play ignored because no media URI is set")
            return
        }

        state = state.copy(playbackState = PlaybackState.PLAYING)
        runOnPlayerThread {
            exoPlayer.prepare()
            exoPlayer.play()
            Log.i(TAG, "Play mapped to ExoPlayer.prepare() and ExoPlayer.play()")
        }
    }

    fun pause() {
        state = state.copy(playbackState = PlaybackState.PAUSED)
        runOnPlayerThread {
            exoPlayer.pause()
            Log.i(TAG, "Pause mapped to ExoPlayer.pause()")
        }
    }

    fun stop() {
        state = state.copy(playbackState = PlaybackState.STOPPED)
        runOnPlayerThread {
            exoPlayer.stop()
            Log.i(TAG, "Stop mapped to ExoPlayer.stop()")
        }
    }

    fun seekTo(positionMs: Long) {
        state = state.copy(currentPositionMs = positionMs.coerceAtLeast(0L))
        runOnPlayerThread {
            exoPlayer.seekTo(positionMs.coerceAtLeast(0L))
            updatePlaybackSnapshot()
            Log.i(TAG, "Seek mapped to ExoPlayer.seekTo($positionMs)")
        }
    }

    fun setVolume(volume: Int) {
        val normalizedVolume = volume.coerceIn(MIN_VOLUME, MAX_VOLUME)
        state = state.copy(volume = normalizedVolume)
        runOnPlayerThread {
            exoPlayer.volume = if (state.muted) 0f else normalizedVolume / MAX_VOLUME.toFloat()
            Log.i(TAG, "Volume set to $normalizedVolume")
        }
    }

    fun setMuted(muted: Boolean) {
        state = state.copy(muted = muted)
        runOnPlayerThread {
            exoPlayer.volume = if (muted) 0f else state.volume / MAX_VOLUME.toFloat()
            Log.i(TAG, "Mute set to $muted")
        }
    }

    fun snapshot(): PlayerSnapshot = state

    fun refreshedSnapshot(): PlayerSnapshot {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            updatePlaybackSnapshot()
            return state
        }

        val latch = CountDownLatch(1)
        mainHandler.post {
            updatePlaybackSnapshot()
            latch.countDown()
        }
        latch.await(SNAPSHOT_REFRESH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return state
    }

    fun release() {
        state = state.copy(playbackState = PlaybackState.STOPPED)
        runOnPlayerThread {
            exoPlayer.release()
            Log.i(TAG, "ExoPlayer released")
        }
    }

    private fun updatePlaybackSnapshot(playbackState: PlaybackState = state.playbackState) {
        val duration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
        val position = exoPlayer.currentPosition.takeIf { it > 0 } ?: 0L
        state = state.copy(
            playbackState = playbackState,
            currentPositionMs = position,
            durationMs = duration,
            volume = if (state.muted) {
                state.volume
            } else {
                (exoPlayer.volume * MAX_VOLUME).roundToInt().coerceIn(MIN_VOLUME, MAX_VOLUME)
            },
        )
    }

    private fun runOnPlayerThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun Int.toDlnaPlaybackState(): PlaybackState =
        when (this) {
            Player.STATE_BUFFERING -> PlaybackState.BUFFERING
            Player.STATE_READY -> if (exoPlayer.isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED
            Player.STATE_ENDED,
            Player.STATE_IDLE,
            -> PlaybackState.STOPPED
            else -> PlaybackState.STOPPED
        }

    companion object {
        private const val TAG = "Media3DlnaPlayer"
        private const val MIN_VOLUME = 0
        private const val MAX_VOLUME = 100
        private const val SNAPSHOT_REFRESH_TIMEOUT_MS = 250L
    }
}

data class PlayerSnapshot(
    val currentUri: String = "",
    val playbackState: PlaybackState = PlaybackState.STOPPED,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int = 100,
    val muted: Boolean = false,
)

enum class PlaybackState {
    STOPPED,
    PLAYING,
    PAUSED,
    BUFFERING,
}
