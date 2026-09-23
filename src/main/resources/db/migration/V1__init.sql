CREATE TABLE exam_session (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    capacity INT NOT NULL,
    seats_remaining INT NOT NULL
);

CREATE TABLE registration (
    id BIGSERIAL PRIMARY KEY,
    exam_session_id BIGINT NOT NULL REFERENCES exam_session (id),
    user_id VARCHAR(255) NOT NULL,
    registered_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_registration_exam_session_id ON registration (exam_session_id);
