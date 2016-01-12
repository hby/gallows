-- name: create-user!
-- creates a new user record
INSERT INTO users
(id, name, email, pass)
VALUES (:id, :name, :email, :pass)

-- name: update-user!
-- update an existing user record
UPDATE users
SET name = :name, email = :email
WHERE id = :id

-- name: get-user
-- retrieve a user given the id.
SELECT * FROM users
WHERE id = :id

-- name: delete-user!
-- delete a user given the id
DELETE FROM users
WHERE id = :id
