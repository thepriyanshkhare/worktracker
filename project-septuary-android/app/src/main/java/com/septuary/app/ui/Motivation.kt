package com.septuary.app.ui

/** Fully offline — a fixed local list, one picked at random each time it's read. No network involved. */
object MotivationQuotes {
    val quotes = listOf(
        "You didn't get to 95kg in a week. You won't get out of it in one either — show up anyway.",
        "The scale lags the work by days. Trust the process, not this morning's number.",
        "Every dose taken on time is a data point your future HbA1c reading will thank you for.",
        "82kg isn't the finish line. It's the checkpoint that proves the system works.",
        "Discipline is choosing between what you want now and what you want most.",
        "You've reversed harder things than a habit. This is just consistency, not talent.",
        "One skipped session doesn't break the plan. Two in a row starts to.",
        "The version of you at 72kg was built in workouts like today's, not the dramatic ones.",
        "Your body is still listening, even on the days you don't feel it.",
        "Progress isn't linear. Adherence is the only thing you actually control today.",
        "Small deficit, repeated daily, beats a perfect week you can't sustain.",
        "You're not starting over — every logged day compounds into the next.",
        "The best time to train was this morning. The next best time is right now.",
        "Nobody sees the 6am walk. Your bloodwork will.",
        "Comfort and change don't share a room. Pick one for today.",
        "You are the only person responsible for closing today's gap — and the only one who can.",
        "A body in motion changes its own chemistry. Move first, feel motivated second.",
        "The plan doesn't need you to feel like it. It needs you to do it.",
        "Waist by waist, kg by kg — this is how V-shapes get built, not overnight.",
        "You've already proven you can execute a hard plan. This is just the next one.",
        "Missed the target today? Log it honestly. Honest data beats a clean-looking lie.",
        "Every rep, every walk, every clean meal is a vote for the body you're building.",
        "The hardest part is already behind you — you decided to start.",
        "Consistency doesn't need to be exciting to work.",
        "You don't have to want to. You just have to do it anyway.",
        "Today's session isn't about today. It's about who shows up in November.",
        "Fatigue is temporary. The habit you're building isn't.",
        "You're not chasing perfect. You're chasing the version of you that doesn't quit.",
        "Treat this like a system, not a sprint — systems don't burn out, willpower does.",
        "The gap between 95 and 72 closes one logged day at a time. Log today."
    )

    fun random(): String = quotes.random()
}
