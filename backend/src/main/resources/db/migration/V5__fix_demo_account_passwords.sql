-- The V2 seed hash did not match any documented password, so the demo accounts could not log in.
-- All three demo accounts now use the password: demo123 (same as the frontend mock login).
-- Verified with Spring Security BCryptPasswordEncoder.matches("demo123", hash) == true.
-- Demo data for coursework only; never reuse these accounts in a real deployment.
UPDATE users
SET password_hash = '$2a$10$TXv5oSPWI9rqp2ifsDv9E.LnviEtnAyYPfxXId4DRw7bSxi9SiRsi'
WHERE username IN ('am_demo', 'bo_demo', 'ds_demo');
