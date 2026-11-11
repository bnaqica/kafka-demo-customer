# customer-records Specification

## Purpose

Stores customers with their personal details and address, and lets clients create, read, update and delete them.

## Requirements

### Requirement: Customer fields
A customer SHALL have `firstName`, `lastName`, `dateOfBirth`, `gender`, `email` and `address` (`street`, `city`, `state`, `zip`), all required, plus optional `middleName` and `address.apartment`, and a list of zero or more `phones`.

#### Scenario: Customer without optional fields
- **WHEN** a customer is created with no `middleName`, no `apartment` and no phones
- **THEN** the API responds 201 and returns the customer with an empty `phones` list

#### Scenario: Missing required field
- **WHEN** a create or update request omits `dateOfBirth`
- **THEN** the API responds 400 and nothing is stored

### Requirement: Normalized values
Before a customer or phone is stored or compared, the service SHALL trim surrounding whitespace from text fields, store `email` in lowercase, and store a blank `middleName` or `address.apartment` as absent.

#### Scenario: Email stored in lowercase
- **WHEN** a customer is created with email ` Ada@Example.COM `
- **THEN** the stored and returned email is `ada@example.com`

#### Scenario: Blank middle name
- **WHEN** a customer is created with `middleName` set to spaces only
- **THEN** the customer is returned without a `middleName`

### Requirement: Field validation
The API SHALL reject with 400 a `gender` other than `MALE`, `FEMALE`, `NON_BINARY` or `PREFER_NOT_TO_SAY`, a `state` that is not two uppercase letters, a `zip` that is not 5 digits or ZIP+4, a `dateOfBirth` not in the past, and an invalid email.

#### Scenario: Invalid zip
- **WHEN** a request has `zip` set to `1234`
- **THEN** the API responds 400

#### Scenario: Future date of birth
- **WHEN** a request has a `dateOfBirth` after today
- **THEN** the API responds 400

### Requirement: Unique email
Customer email addresses SHALL be unique regardless of case.

#### Scenario: Duplicate on create
- **WHEN** a customer is created with an email another customer has, in different case
- **THEN** the API responds 409 and nothing is stored

#### Scenario: Duplicate on update
- **WHEN** a customer is updated to an email another customer has
- **THEN** the API responds 409 and the customer is unchanged

### Requirement: Listing customers
`GET /api/customers` SHALL return all customers, newest first, each with its address and phones. `GET /api/customers/{id}` SHALL return one customer.

#### Scenario: Unknown customer
- **WHEN** a client requests a customer id that does not exist
- **THEN** the API responds 404

### Requirement: Replacing a customer
`PUT /api/customers/{id}` SHALL replace the customer's fields and make its phones match the request's list.

#### Scenario: Phone removed from the list
- **WHEN** a PUT request leaves out a phone the customer has
- **THEN** that phone is removed from the customer

### Requirement: Deleting a customer
`DELETE /api/customers/{id}` SHALL remove the customer and all of its phones and respond 204.

#### Scenario: Customer with phones deleted
- **WHEN** a customer with two phones is deleted
- **THEN** the customer and both phones no longer exist

### Requirement: Local seed data
The `local` profile SHALL load a fixed set of sample customers with addresses and phones. Other profiles SHALL NOT load them, and loading them SHALL NOT publish events.

#### Scenario: Local start
- **WHEN** the service starts in the `local` profile against an empty database
- **THEN** the sample customers are listed and no event is published for them

#### Scenario: Non-local start
- **WHEN** the service starts without the `local` profile
- **THEN** no sample customers are created
