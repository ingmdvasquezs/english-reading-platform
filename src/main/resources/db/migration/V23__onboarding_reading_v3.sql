-- V23: Deactivate previous onboarding readings and insert onboarding reading v3

UPDATE onboarding_readings
SET active = FALSE
WHERE active = TRUE;

INSERT INTO onboarding_readings (title, content, active, version, created_at)
VALUES (
    'Finding a Voice in a New Harbor',
    'Elena arrived in the quiet harbor town on a rainy morning in early autumn, carrying a small suitcase and a quiet desire to build a fresh life away from the rush of the capital. Her modest apartment stood near the train station, just above a busy street where local merchants sold fresh food and flowers. Every day before starting work, she walked down the cobblestone road toward the neighborhood market to buy fruit and warm bread at the local bakery. At first, simple daily routines required deliberate attention. She carried a pocket notebook filled with everyday words for household items, transport, and weather. When the friendly baker greeted her with a warm smile and spoke slowly, Elena felt genuinely encouraged. Each morning walk helped her discover the gentle rhythm of the coastal community, gradually turning unfamiliar streets into comfortable paths where ordinary interactions brought a welcoming sense of calm and quiet purpose.

Within a month, Elena accepted a temporary position organizing historical archives at the community library. The new environment presented both an unexpected challenge and a valuable opportunity. Her colleagues shared lively anecdotes during lunch breaks, discussing local traditions, regional festivals, and ongoing civic initiatives. At first, she hesitated to participate actively in conversations, worried that she might choose the wrong expression or misunderstand an informal phrase. However, a supportive coworker encouraged her to contribute without fear of making mistakes. Elena soon realized that people valued genuine curiosity and honest engagement far more than effortless accuracy. She began attending an evening reading circle at the community center, where neighbors gathered to debate books and personal experiences. Sharing her perspective during these discussions expanded her confidence, teaching her that meaningful communication grows through shared laughter, patient listening, and consistent practice.

By winter, Elena approached language learning with deeper awareness and reflective maturity. Reading local history taught her to interpret delicate distinctions between silence and solitude, noticing subtle shades of meaning that casual dictionaries rarely capture. Rather than viewing complex expressions as frustrating obstacles, she welcomed ambiguity as an invitation to adapt and cultivate cultural intuition. Watching boats return to the pier beneath a cold sunset, she recognized how resilient her confidence had become despite lingering uncertainty. Language was no longer an abstract set of grammatical rules to memorize in isolation; it had transformed into an authentic bridge connecting her thoughts to the harbor community. Looking forward to the coming spring, Elena felt a quiet conviction that every unfamiliar phrase would simply represent another opportunity to learn, observe, and belong.',
    TRUE,
    3,
    CURRENT_TIMESTAMP
);
