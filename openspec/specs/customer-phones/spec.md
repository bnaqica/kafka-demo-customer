# customer-phones Specification

## Purpose

Lets clients manage a customer's phone numbers individually, in addition to setting them with the customer.

## Requirements

### Requirement: Phone fields
A phone SHALL have a `type` of `HOME`, `WORK` or `CELL` and a `number` of 7 to 20 characters containing only digits, spaces and `+ - ( )`.

#### Scenario: Invalid type
- **WHEN** a request has phone type `FAX`
- **THEN** the API responds 400

### Requirement: Phone CRUD
The API SHALL support `POST` and `GET` on `/api/customers/{customerId}/phones` and `GET`, `PUT` and `DELETE` on `/api/customers/{customerId}/phones/{phoneId}`.

#### Scenario: Add a phone
- **WHEN** a phone is posted to an existing customer
- **THEN** the API responds 201 with the phone and its id

#### Scenario: Phone of another customer
- **WHEN** a client requests a phone id under a customer that does not own it
- **THEN** the API responds 404

#### Scenario: Unknown customer
- **WHEN** a phone is posted to a customer that does not exist
- **THEN** the API responds 404

### Requirement: No duplicate phones on a customer
A customer SHALL NOT have two phones with the same type and number.

#### Scenario: Duplicate through the phones API
- **WHEN** a phone is posted with a type and number the customer already has
- **THEN** the API responds 409

#### Scenario: Duplicate in a customer request
- **WHEN** a customer create or update request lists the same type and number twice
- **THEN** the API responds 400
