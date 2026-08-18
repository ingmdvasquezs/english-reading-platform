CREATE TABLE users (
                       id UUID PRIMARY KEY,
                       name VARCHAR(100) NOT NULL,
                       email VARCHAR(150) NOT NULL,
                       created_at TIMESTAMP NOT NULL,

                       CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE words (
                       id UUID PRIMARY KEY,
                       normalized_value VARCHAR(100) NOT NULL,
                       language VARCHAR(10) NOT NULL,
                       created_at TIMESTAMP NOT NULL,

                       CONSTRAINT uk_words_normalized_value UNIQUE (normalized_value)
);

CREATE TABLE readings (
                          id UUID PRIMARY KEY,
                          user_id UUID NOT NULL,
                          title VARCHAR(250) NOT NULL,
                          content TEXT NOT NULL,
                          language VARCHAR(10) NOT NULL,
                          created_at TIMESTAMP NOT NULL,

                          CONSTRAINT fk_readings_user
                              FOREIGN KEY (user_id)
                                  REFERENCES users(id)
);

CREATE TABLE user_vocabulary (
                                 id UUID PRIMARY KEY,
                                 user_id UUID NOT NULL,
                                 word_id UUID NOT NULL,
                                 status VARCHAR(20) NOT NULL,
                                 first_seen_at TIMESTAMP NOT NULL,
                                 learned_at TIMESTAMP,

                                 CONSTRAINT fk_user_vocabulary_user
                                     FOREIGN KEY (user_id)
                                         REFERENCES users(id),

                                 CONSTRAINT fk_user_vocabulary_word
                                     FOREIGN KEY (word_id)
                                         REFERENCES words(id),

                                 CONSTRAINT uk_user_vocabulary_user_word
                                     UNIQUE (user_id, word_id)
);