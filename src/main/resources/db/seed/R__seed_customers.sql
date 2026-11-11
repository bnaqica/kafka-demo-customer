-- Sample data for the `local` profile only. Plain SQL, so no events are published for these rows.
-- Repeatable rather than versioned, so a seed version can never block a later migration.
INSERT INTO customer (id, first_name, middle_name, last_name, date_of_birth, gender, email, street, apartment, city, state, zip, created_at, updated_at)
SELECT seed.id::uuid, seed.first_name, seed.middle_name, seed.last_name, seed.date_of_birth, seed.gender, seed.email,
       seed.street, seed.apartment, seed.city, seed.state, seed.zip, now(), now()
FROM (VALUES
    ('30ffb4e6-5e2d-5588-9ffb-cb1432ed6612', 'Ada', 'King', 'Lovelace', DATE '1985-12-10', 'FEMALE', 'ada.lovelace@example.com', '12 Analytical Way', NULL, 'Albany', 'NY', '12207'),
    ('33353b08-b7e9-5750-83fd-22af9e728706', 'Alan', 'Mathison', 'Turing', DATE '1982-06-23', 'MALE', 'alan.turing@example.com', '45 Enigma Rd', 'Apt 3', 'Boston', 'MA', '02108'),
    ('1ede66f0-cce3-5b93-b11a-53a54d533b08', 'Grace', 'Brewster', 'Hopper', DATE '1976-12-09', 'FEMALE', 'grace.hopper@example.com', '7 Compiler Ct', NULL, 'Arlington', 'VA', '22201'),
    ('761f2210-05ad-5ed5-8a66-771a54bb778a', 'Linus', NULL, 'Pauling', DATE '1990-02-28', 'MALE', 'linus.pauling@example.com', '88 Chemistry Ln', NULL, 'Portland', 'OR', '97201'),
    ('95be6d2b-88f8-533f-a3ff-3462173a0633', 'Katherine', 'Coleman', 'Johnson', DATE '1978-08-26', 'FEMALE', 'katherine.johnson@example.com', '301 Orbit Dr', 'Unit 12', 'Hampton', 'VA', '23669'),
    ('2dcbd16d-726d-58a9-8352-96fae133aba7', 'Tim', NULL, 'Lee', DATE '1995-06-08', 'NON_BINARY', 'tim.lee@example.com', '20 Web St', NULL, 'Austin', 'TX', '78701'),
    ('e18452d2-cf65-5260-8d65-a6cb66545cd7', 'Margaret', 'Heafield', 'Hamilton', DATE '1980-08-17', 'FEMALE', 'margaret.hamilton@example.com', '11 Apollo Pl', NULL, 'Cambridge', 'MA', '02139'),
    ('b59d71ac-836f-518a-a891-6f036f732dd1', 'Dennis', 'MacAlistair', 'Ritchie', DATE '1972-09-09', 'MALE', 'dennis.ritchie@example.com', '5 Kernel Ave', NULL, 'Summit', 'NJ', '07901'),
    ('2904a53e-376c-5fdb-a1f2-c9620a300e4e', 'Radia', 'Joy', 'Perlman', DATE '1988-12-18', 'PREFER_NOT_TO_SAY', 'radia.perlman@example.com', '9 Spanning Tree Blvd', 'Suite 2', 'Seattle', 'WA', '98101'),
    ('5a11bfe9-4d13-5a42-bf23-d9a505fe72b7', 'Barbara', NULL, 'Liskov', DATE '1974-11-07', 'FEMALE', 'barbara.liskov@example.com', '60 Substitution St', NULL, 'Denver', 'CO', '80202'),
    ('39bda220-5517-5b85-b5d1-b06421349c35', 'Edsger', 'Wybe', 'Dijkstra', DATE '1970-05-11', 'MALE', 'edsger.dijkstra@example.com', '3 Shortest Path', NULL, 'Austin', 'TX', '78702'),
    ('7c33e0cc-fb2a-5e3f-a1ed-c326e1793414', 'Annie', NULL, 'Easley', DATE '1992-04-23', 'FEMALE', 'annie.easley@example.com', '72 Rocket Rd', NULL, 'Cleveland', 'OH', '44114')
) AS seed (id, first_name, middle_name, last_name, date_of_birth, gender, email, street, apartment, city, state, zip)
ON CONFLICT DO NOTHING;

-- The join skips phones for any sample customer that has since been deleted.
INSERT INTO customer_phone (id, customer_id, type, number)
SELECT seed.id::uuid, customer.id, seed.type, seed.number
FROM (VALUES
    ('574791cb-7077-51eb-9955-57de007cc023', '30ffb4e6-5e2d-5588-9ffb-cb1432ed6612', 'CELL', '518-555-0101'),
    ('36d3cd29-ab21-5387-8c3a-b24989f60fa4', '30ffb4e6-5e2d-5588-9ffb-cb1432ed6612', 'HOME', '518-555-0102'),
    ('5bebfa97-fe1c-58f8-a27c-e16e1ee7ee80', '33353b08-b7e9-5750-83fd-22af9e728706', 'CELL', '617-555-0103'),
    ('210d90df-70fb-5147-a0c4-cea307467f04', '1ede66f0-cce3-5b93-b11a-53a54d533b08', 'WORK', '703-555-0104'),
    ('e0268fa7-11f2-528f-89f8-88f8413e4cde', '1ede66f0-cce3-5b93-b11a-53a54d533b08', 'CELL', '703-555-0105'),
    ('9ab38cbd-3261-555c-96c1-36047535344c', '761f2210-05ad-5ed5-8a66-771a54bb778a', 'HOME', '503-555-0106'),
    ('93524aba-e553-52cb-90ff-f7482c09b157', '95be6d2b-88f8-533f-a3ff-3462173a0633', 'CELL', '757-555-0107'),
    ('574fdcd4-aa31-5db0-b2f8-a152e914ef12', '2dcbd16d-726d-58a9-8352-96fae133aba7', 'CELL', '512-555-0108'),
    ('328c200d-e132-5504-8df2-f06c57168747', '2dcbd16d-726d-58a9-8352-96fae133aba7', 'WORK', '512-555-0109'),
    ('5941bbb8-227b-5c7d-a19e-c73aa038989d', 'e18452d2-cf65-5260-8d65-a6cb66545cd7', 'HOME', '617-555-0110'),
    ('c03a20f7-dbf8-50db-a425-2429f1849d9b', 'b59d71ac-836f-518a-a891-6f036f732dd1', 'WORK', '908-555-0111'),
    ('bd9ef2e0-7849-5996-a853-2f871d939a05', '2904a53e-376c-5fdb-a1f2-c9620a300e4e', 'CELL', '206-555-0112'),
    ('f63fddae-d8ea-5d58-8491-c6acb0bd8fac', '5a11bfe9-4d13-5a42-bf23-d9a505fe72b7', 'HOME', '303-555-0113'),
    ('834346db-4c49-58a3-abd0-a079bd7046f2', '5a11bfe9-4d13-5a42-bf23-d9a505fe72b7', 'CELL', '303-555-0114'),
    ('a357cd55-a1e2-5e31-83f3-8595f038c38d', '39bda220-5517-5b85-b5d1-b06421349c35', 'WORK', '512-555-0115'),
    ('c0785052-e4d2-53b4-b0df-bcfbc71a85c9', '7c33e0cc-fb2a-5e3f-a1ed-c326e1793414', 'CELL', '216-555-0116')
) AS seed (id, customer_id, type, number)
JOIN customer ON customer.id = seed.customer_id::uuid
ON CONFLICT DO NOTHING;
