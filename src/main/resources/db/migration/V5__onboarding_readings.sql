CREATE TABLE onboarding_readings (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(250) NOT NULL,
    content TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_onboarding_readings_active_version
    ON onboarding_readings (active, version DESC, created_at DESC, id DESC);

INSERT INTO onboarding_readings (title, content)
VALUES (
    'A Different Way to Learn',
    'Daniel had always wanted to speak English well. Every morning, before going to work, he spent a few minutes reading short stories on his phone. At first, he understood only simple sentences about family, food, work, and daily activities. When he found a difficult word, he usually tried to guess its meaning from the sentence before looking it up.

After several months, reading became easier. Daniel started following news articles and watching videos about science, travel, and technology. He could understand the main ideas, although unfamiliar expressions still appeared frequently. Instead of stopping every time he encountered a new word, he learned to continue reading and focus on the overall meaning.

One evening, Daniel joined an online conversation with people from different countries. He was nervous because understanding written English was much easier than responding quickly in a real conversation. Nevertheless, he introduced himself and gradually became more confident. Some participants spoke clearly, while others used expressions he had never encountered before.

The experience made him realize that learning a language involves more than memorizing vocabulary. Context, curiosity, and consistent practice can significantly improve comprehension. Even when a learner cannot recognize every expression, they may still interpret complex ideas by connecting information and making reasonable assumptions.

Over time, Daniel developed a more sophisticated understanding of the language. He began noticing subtle differences between similar expressions and recognizing how the writer''s intention could influence the meaning of a sentence. What had once seemed overwhelming gradually became manageable.

Daniel still encountered unfamiliar vocabulary, but he no longer considered it an obstacle. Instead, uncertainty became part of the learning process, encouraging him to explore ideas that were increasingly challenging.'
);
