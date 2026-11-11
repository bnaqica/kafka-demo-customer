# Spec Delta

## Purpose

Defines which event is published to the customer-change-event topic for each change and what the event carries.

## ADDED Requirements

### Requirement: One event per changing call
Each API call that changes a customer or phone SHALL publish exactly one event to `customer-change-event`, keyed by customer id, after the database commit. A call that changes nothing or fails SHALL publish no event.

#### Scenario: PUT with identical values
- **WHEN** a customer or phone PUT supplies the values already stored
- **THEN** the API responds 200 and no event is published

#### Scenario: PUT differing only in whitespace or email case
- **WHEN** a customer PUT differs from the stored customer only by surrounding whitespace or the case of the email
- **THEN** the API responds 200 and no event is published

#### Scenario: Failed request
- **WHEN** a request fails validation or conflicts
- **THEN** no event is published

### Requirement: Publishing failure does not fail the request
If publishing fails after the database commit, the change SHALL be kept, the API SHALL respond as if publishing succeeded, and the failure SHALL be logged. The event SHALL NOT be retried, so each change publishes at most one event.

#### Scenario: Kafka unavailable
- **WHEN** a customer is created while Kafka cannot be reached
- **THEN** the API responds 201, the customer is stored and the failure is logged

### Requirement: Event type by change
POST customer SHALL publish `customer-addition` and DELETE customer `customer-delete`, regardless of phones. The phones API SHALL publish `customer-phone-add`, `customer-phone-change` or `customer-phone-delete`.

#### Scenario: Customer created with phones
- **WHEN** a customer is created with two phones
- **THEN** one `customer-addition` event is published

#### Scenario: Customer deleted with phones
- **WHEN** a customer with two phones is deleted
- **THEN** one `customer-delete` event is published

#### Scenario: Phone added through the phones API
- **WHEN** a phone is posted
- **THEN** one `customer-phone-add` event is published

### Requirement: Event type for a customer PUT
A customer PUT SHALL publish the most specific type covering the whole change: `customer-address-change` for address only; a phone type for exactly one phone added, removed, or replaced by one of the same type; otherwise `customer-change`.

#### Scenario: Address only
- **WHEN** a PUT changes only the address
- **THEN** one `customer-address-change` event is published

#### Scenario: One phone added
- **WHEN** a PUT changes only the phones by adding one
- **THEN** one `customer-phone-add` event is published

#### Scenario: One phone replaced by the same type
- **WHEN** a PUT removes one CELL phone and adds another CELL phone
- **THEN** one `customer-phone-change` event is published

#### Scenario: Several areas
- **WHEN** a PUT changes the last name and the address
- **THEN** one `customer-change` event is published listing both areas

#### Scenario: Several phones
- **WHEN** a PUT adds two phones
- **THEN** one `customer-change` event is published

### Requirement: Event content
Every event SHALL carry `eventId`, `eventType`, `occurredAt`, `customerId`, `firstName`, `lastName`, `email` and `state` as they are after the change, or as they were for `customer-delete`, plus `changes`. Date of birth SHALL NOT be included.

#### Scenario: Delete event still has a recipient
- **WHEN** a customer is deleted
- **THEN** the event carries the customer's name, email and state

#### Scenario: Email changed
- **WHEN** a PUT changes the email
- **THEN** the event's `email` is the new address

### Requirement: Changes by type
`changes` SHALL be empty for `customer-addition` and `customer-delete`, hold `fields` for `customer-change`, `address` for `customer-address-change`, `phone` for phone add and delete, and `previousPhone` and `phone` for phone change. Parts that do not apply SHALL be left out of the JSON, so `customer-addition` and `customer-delete` carry `"changes": {}`.

#### Scenario: Phone change detail
- **WHEN** a phone's number is changed
- **THEN** the event's `changes` holds the previous and new phone

#### Scenario: Addition has no change detail
- **WHEN** a `customer-addition` event is published
- **THEN** its `changes` is an empty object

### Requirement: Change detail format
`changes.fields` SHALL list, in this order, those of `firstName`, `middleName`, `lastName`, `dateOfBirth`, `gender`, `email`, `address` and `phones` that changed. `changes.address` SHALL be the address after the change, with `street`, `apartment`, `city`, `state` and `zip`. A phone SHALL be given as its `type` and `number`.

#### Scenario: Fields listed
- **WHEN** a PUT changes the last name and the address
- **THEN** `changes.fields` is `["lastName", "address"]`

#### Scenario: Address change detail
- **WHEN** a `customer-address-change` event is published
- **THEN** `changes.address` holds the new address and no other part of `changes` is present
