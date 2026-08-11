package com.shsw228.showdeck.alert

import java.time.Duration
import java.time.LocalDateTime

/**
 * カウントダウンタイマー 1 本。
 *
 * 以前は 1 本しか持てなかった（[RunningTimer]）。台所で使うと、麺を茹でながら
 * 別のものを蒸す、という当たり前のことができない。同時に複数走らせられる形に
 * 作り直したのがこれ。
 *
 * 動作中は「いつ鳴るか」で持ち、止めているときは「あとどれだけか」で持つ。
 * 残り秒を毎秒書き換える形にすると、画面が消えている間や再起動をまたいだ
 * ときにずれる。時刻で持てば、次に見たときに正しい値が出る。
 */
data class CountdownTimer(
    val id: Long,
    val label: String,
    val total: Duration,
    /** 動作中の終了時刻。止めているときは null。 */
    val endsAt: LocalDateTime?,
    /** 止めているときの残り。動作中は null。 */
    val pausedRemaining: Duration?,
) {
    val isRunning: Boolean get() = endsAt != null

    fun remaining(now: LocalDateTime): Duration = when {
        endsAt != null -> Duration.between(now, endsAt).coerceAtLeast(Duration.ZERO)
        else -> pausedRemaining ?: total
    }

    /** まだ一度も動かしていないか。画面では `READY` と出す。 */
    fun isFresh(now: LocalDateTime): Boolean = !isRunning && remaining(now) == total

    fun isDone(now: LocalDateTime): Boolean = remaining(now) == Duration.ZERO

    /** 経過の割合。進捗バーはこちらを使う。 */
    fun elapsedFraction(now: LocalDateTime): Float {
        val totalSeconds = total.seconds
        if (totalSeconds <= 0) return 1f
        return (1f - remaining(now).seconds.toFloat() / totalSeconds).coerceIn(0f, 1f)
    }

    fun start(now: LocalDateTime): CountdownTimer {
        val left = remaining(now).takeIf { it > Duration.ZERO } ?: total
        return copy(endsAt = now.plus(left), pausedRemaining = null)
    }

    fun pause(now: LocalDateTime): CountdownTimer =
        copy(endsAt = null, pausedRemaining = remaining(now))

    fun reset(): CountdownTimer = copy(endsAt = null, pausedRemaining = total)

    fun toggle(now: LocalDateTime): CountdownTimer = if (isRunning) pause(now) else start(now)

    /** 残りの表示。1 時間を超えたら `H:MM:SS`、そうでなければ `MM:SS`。 */
    fun display(now: LocalDateTime): String {
        val left = remaining(now)
        val hours = left.toHours()
        val minutes = left.toMinutes() % 60
        val seconds = left.seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}

/**
 * 走っているタイマーの一覧。
 *
 * 純粋な関数として持つ。[com.shsw228.showdeck.DeckViewModel] が状態を持ち、
 * ここは「今の一覧と時刻を渡すと次の一覧を返す」だけ。以前グローバルな
 * 可変シングルトンでやって、状態の持ち主が二重になったので同じ轍は踏まない。
 */
object Countdowns {

    /**
     * 一度に画面へ収まる枚数。**本数の上限ではない。**
     *
     * これを超えたぶんは Timers 画面を横スクロールして見る。
     */
    const val VISIBLE = 3

    /**
     * 保持する本数の上限。
     *
     * 以前は 3 本で打ち止めにして、4 本目を足したら古いものを押し出していた。
     * 押し出されたタイマーは画面から消えるだけで、走っていたことすら
     * 分からなくなる。**黙って捨てるくらいなら並べてスクロールさせる。**
     *
     * それでも上限を置くのは、押し間違いで増え続けるのを止めるため。
     * 台所で 8 本を同時に回すことはない。
     */
    const val MAX = 8

    fun add(
        timers: List<CountdownTimer>,
        label: String,
        minutes: Int,
        now: LocalDateTime,
        id: Long = now.toLocalTime().toNanoOfDay(),
    ): List<CountdownTimer> {
        if (timers.size >= MAX) return timers
        val fresh = CountdownTimer(
            id = id,
            label = label.ifBlank { "$minutes min" },
            total = Duration.ofMinutes(minutes.toLong()),
            endsAt = now.plusMinutes(minutes.toLong()),
            pausedRemaining = null,
        )
        return timers + fresh
    }

    fun update(
        timers: List<CountdownTimer>,
        id: Long,
        transform: (CountdownTimer) -> CountdownTimer,
    ): List<CountdownTimer> = timers.map { if (it.id == id) transform(it) else it }

    fun remove(timers: List<CountdownTimer>, id: Long): List<CountdownTimer> =
        timers.filterNot { it.id == id }

    /**
     * 鳴らすべきものを取り出す。
     *
     * 鳴らしたら止まった状態にして残す。消してしまうと、席を外している間に
     * 鳴り終わったタイマーが画面から消え、何が終わったのか分からなくなる。
     */
    fun fire(
        timers: List<CountdownTimer>,
        now: LocalDateTime,
    ): Pair<List<CountdownTimer>, CountdownTimer?> {
        val done = timers.firstOrNull { it.isRunning && it.isDone(now) } ?: return timers to null
        return update(timers, done.id) { it.copy(endsAt = null, pausedRemaining = Duration.ZERO) } to done
    }
}
