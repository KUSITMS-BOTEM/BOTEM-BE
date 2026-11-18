ALTER TABLE fundings
    MODIFY COLUMN title VARCHAR(20) NULL,
    ADD COLUMN show_on_recipient_profile BOOLEAN NOT NULL DEFAULT FALSE AFTER promise,
    ADD COLUMN notify_recipient_friends BOOLEAN NOT NULL DEFAULT FALSE AFTER show_on_recipient_profile;
