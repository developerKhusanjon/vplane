-- Add constraint to prevent self-sending notes
ALTER TABLE notes ADD CONSTRAINT chk_notes_different_users
    CHECK (sender_id != receiver_id);

 -- Function to update updated_at timestamp
 CREATE OR REPLACE FUNCTION update_updated_at_column()
 RETURNS TRIGGER AS $$
 BEGIN
     NEW.updated_at = NOW();
     RETURN NEW;
 END;
 $$ language 'plpgsql';

 -- Triggers for automatic updated_at
 CREATE TRIGGER update_users_updated_at
     BEFORE UPDATE ON users
     FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

 CREATE TRIGGER update_notes_updated_at
     BEFORE UPDATE ON notes
     FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();