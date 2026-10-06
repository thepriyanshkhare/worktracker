package com.septuary.app.data

/**
 * Seed data extracted from "Project Septuary" (Claude Docs) on 29 Sep 2026.
 * Loaded once into the encrypted database on first unlock, only if empty.
 */
object SeedData {

    val medications = listOf(
        MedicationEntity("neurobion", "Neurobion Forte", "B-complex incl. B12 (replacing Tab Homin)",
            "Once daily, after breakfast", "08:30", "daily", null, null),
        MedicationEntity("drise", "Cap D Rise 60K", "Vitamin D3",
            "Every Sunday, after breakfast. Weekly through ~22 Nov 2026, then switch to monthly.",
            "08:30", "weekly:0", "2026-09-30", null),
        MedicationEntity("olmesar", "Olmesar M-25", "Olmesartan + Metoprolol (BP)",
            "Once daily, same time every day — best practice default, confirm against strip",
            "08:30", "daily", null, null),
        MedicationEntity("foracort-am", "Foracort Inhaler", "Formoterol + Budesonide — 2 puffs",
            "Morning — rinse mouth after use", "08:00", "daily", null, null),
        MedicationEntity("udiliv-lunch", "Udiliv", "UDCA — dose 1 of 2",
            "After lunch", "13:30", "daily", null, null),
        MedicationEntity("istamet-lunch", "Tab Istamet 50/500", "Vildagliptin + Metformin — dose 1 of 2",
            "After lunch", "13:30", "daily", null, null),
        MedicationEntity("udiliv-dinner", "Udiliv", "UDCA — dose 2 of 2",
            "After dinner", "21:00", "daily", null, null),
        MedicationEntity("istamet-dinner", "Tab Istamet 50/500", "Vildagliptin + Metformin — dose 2 of 2",
            "After dinner", "21:00", "daily", null, null),
        MedicationEntity("roseday", "Roseday-F 10", "Rosuvastatin + Fenofibrate",
            "After dinner — fenofibrate needs the fat in the meal", "21:00", "daily", null, null),
        MedicationEntity("icos", "Cap ICOS 1g", "Omega-3",
            "After dinner — 1-month course only, stop ~29 Oct 2026", "21:00", "daily", null, "2026-10-29"),
        MedicationEntity("foracort-pm", "Foracort Inhaler", "Formoterol + Budesonide — 2 puffs",
            "Night — rinse mouth after use", "21:30", "daily", null, null),

        // Wellness reminders — same alarm/dose-log machinery as medications, grouped separately
        // on Today and counted toward the "Food" (not "Medicine") Trends bucket.
        MedicationEntity(
            id = "coffee-1", name = "Black Coffee", detail = "1 of 3 — no sugar/cream",
            instruction = "Skip if you're already feeling wired or it's this late", time = "08:15",
            days = "daily", startDate = null, endDate = null, category = "coffee"
        ),
        MedicationEntity(
            id = "coffee-2", name = "Black Coffee", detail = "2 of 3 — no sugar/cream",
            instruction = "Skip if you're already feeling wired or it's this late", time = "12:30",
            days = "daily", startDate = null, endDate = null, category = "coffee"
        ),
        MedicationEntity(
            id = "coffee-3", name = "Black Coffee", detail = "3 of 3 — no sugar/cream",
            instruction = "Last one for the day — keep it away from bedtime", time = "16:30",
            days = "daily", startDate = null, endDate = null, category = "coffee"
        ),
        MedicationEntity(
            id = "greentea-1", name = "Green Tea", detail = "1 of 3",
            instruction = "Between meals, not right on top of coffee", time = "10:00",
            days = "daily", startDate = null, endDate = null, category = "tea"
        ),
        MedicationEntity(
            id = "greentea-2", name = "Green Tea", detail = "2 of 3",
            instruction = "Between meals, not right on top of coffee", time = "15:00",
            days = "daily", startDate = null, endDate = null, category = "tea"
        ),
        MedicationEntity(
            id = "greentea-3", name = "Green Tea", detail = "3 of 3",
            instruction = "Keep it away from bedtime", time = "18:30",
            days = "daily", startDate = null, endDate = null, category = "tea"
        ),
        MedicationEntity(
            id = "meal-breakfast", name = "Breakfast", detail = "On-time meal reminder",
            instruction = "Eat before the 08:15/08:30 medication doses", time = "08:00",
            days = "daily", startDate = null, endDate = null, category = "meal"
        ),
        MedicationEntity(
            id = "meal-lunch", name = "Lunch", detail = "On-time meal reminder",
            instruction = "Eat before the 13:30 after-lunch doses", time = "13:00",
            days = "daily", startDate = null, endDate = null, category = "meal"
        ),
        MedicationEntity(
            id = "meal-dinner", name = "Dinner", detail = "On-time meal reminder",
            instruction = "Eat before the 21:00 after-dinner doses", time = "20:30",
            days = "daily", startDate = null, endDate = null, category = "meal"
        )
    )

    val weightLog = listOf(
        WeightEntity(date = "2026-09-27", time = "22:00", kg = 93.8, note = "Off-protocol (evening) — early water-weight drop, don't extrapolate"),
        WeightEntity(date = "2026-09-28", time = "06:30", kg = 93.2, note = "On-protocol (morning, pre-food)"),
        WeightEntity(date = "2026-09-29", time = "20:49", kg = 92.75, note = "Off-protocol (evening, new BIA scale) — BF% 28.9%, BMI 27.4, visceral fat index 10"),
        WeightEntity(date = "2026-09-30", time = "06:30", kg = 91.60, note = "On-protocol (morning, pre-food)"),
        WeightEntity(date = "2026-10-03", time = "08:44", kg = 92.30, note = "On-protocol (Saturday morning, pre-food)"),
        WeightEntity(date = "2026-10-04", time = "20:00", kg = 91.45, note = "Off-protocol (evening)"),
        WeightEntity(date = "2026-10-05", time = "08:00", kg = 90.45, note = "Time not logged"),
        WeightEntity(date = "2026-10-06", time = "08:00", kg = 89.80, note = "Time not logged")
    )

    val glucoseLog = listOf(
        GlucoseEntity(date = "2026-09-25", time = "23:15", type = "PP (night)", value = 174),
        GlucoseEntity(date = "2026-09-26", time = "08:30", type = "Fasting", value = 193),
        GlucoseEntity(date = "2026-09-26", time = "16:00", type = "PP", value = 164),
        GlucoseEntity(date = "2026-09-26", time = "21:15", type = "PP", value = 166),
        GlucoseEntity(date = "2026-09-27", time = "08:30", type = "Fasting", value = 165),
        GlucoseEntity(date = "2026-09-27", time = "16:55", type = "PP", value = 167),
        GlucoseEntity(date = "2026-09-27", time = "21:25", type = "PP", value = 168),
        GlucoseEntity(date = "2026-09-28", time = "10:10", type = "PP", value = 178),
        GlucoseEntity(date = "2026-09-28", time = "22:10", type = "PP", value = 178),
        GlucoseEntity(date = "2026-09-29", time = "08:00", type = "Fasting", value = 168)
    )

    val goals = listOf(
        GoalEntity(metric = "Weight", baseline = "95.0 kg (27 Sep)", phase1Target = "83.4 kg (29 Nov)", longTerm = "72.0 kg (from 1 Feb 2027, held indefinitely)"),
        GoalEntity(metric = "Waist", baseline = "42.0 in", phase1Target = "36.0 in (user-set stretch target, 29 Nov)", longTerm = "30.0 in (WHtR 0.41, athletic-lean)"),
        GoalEntity(metric = "Hips", baseline = "43.0 in", phase1Target = "~41.0-41.5 in", longTerm = "—"),
        GoalEntity(metric = "HbA1c", baseline = "10.5%", phase1Target = "~7.5-8.5% (12 weeks)", longTerm = "5.6% (full remission, 12-24 months; condition: Type 2 not autoimmune)"),
        GoalEntity(metric = "Body fat %", baseline = "~28.9% (BIA, 29 Sep)", phase1Target = "—", longTerm = "12-15% (Athletic Physique / V-taper)")
    )

    val flags = listOf(
        FlagEntity(text = "Tab Homin — identified as B12, substituted with Neurobion Forte", resolved = true),
        FlagEntity(text = "Rosuvas F 10 — not started, continuing Roseday-F only (confirm with Dr. Harshitha)", resolved = true),
        FlagEntity(text = "Udiliv 300 — confirmed active as a 1-month course (6 Oct); get an extend-or-stop decision before it ends", resolved = true),
        FlagEntity(text = "Two-doctor coordination gap — Dr. Ayer's workup vs. Dr. Harshitha's additions not cross-confirmed", resolved = false),
        FlagEntity(text = "Echo/Holter — still open; 4 independent tachycardia readings (106-135 bpm) across 3 doctors", resolved = false),
        FlagEntity(text = "Diabetes type — confirmed Type 2 (6 Oct)", resolved = true),
        FlagEntity(text = "FibroScan Liver, urine microalbumin — pending completion", resolved = false)
    )
}
