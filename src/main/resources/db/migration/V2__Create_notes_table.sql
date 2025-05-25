CREATE TYPE note_status AS ENUM ('pending', 'done', 'not_done');

CREATE TABLE notes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    status note_status DEFAULT 'pending',
    deadline TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_notes_sender_id ON notes(sender_id);
CREATE INDEX idx_notes_receiver_id ON notes(receiver_id);
CREATE INDEX idx_notes_status ON notes(status);
CREATE INDEX idx_notes_deadline ON notes(deadline);
CREATE INDEX idx_notes_created_at ON notes(created_at);