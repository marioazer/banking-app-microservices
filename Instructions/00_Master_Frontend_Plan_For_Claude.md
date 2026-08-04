# Master Frontend Development Plan: Banking Application
**Target Environment:** Angular, TypeScript, HTML, CSS interacting with a Java/PostgreSQL Backend.
**Methodology:** Agile, TDD, Modular Component-Driven Design.
**Execution Engine:** Optimized for Claude Code (Claude 3.5 Sonnet).

> **Instructions for Claude Code:** 
> Read this document. Do not proceed to the next phase until the human user confirms the current phase is fully completed and verified. Maintain context of the backend API at all times.

---

## Phase 1: Discovery & Requirements Gathering (Pre-Implementation)
**Goal:** Understand the backend, define front-end scope, and create rigid specifications to avoid rabbit holes.

*   **Step 1.1: Backend API Intake**
    *   **Action:** Claude to scan existing Java backend directories (Controllers, Entities, DTOs, application.properties).
    *   **Output:** Generate `01_backend_api_specs.txt` detailing all RESTful API endpoints, expected request/response payloads, auth mechanisms (e.g., JWT), and database schemas (PostgreSQL).
*   **Step 1.2: UI/UX Page Mapping (Iterative)**
    *   **Action:** Based on API specs, Claude to propose a list of necessary Angular routes/pages (e.g., `/login`, `/dashboard`, `/transfer`, `/accounts`).
    *   **Output:** Human and Claude iterate on this until approved. Output saved to `02_frontend_pages_map.txt`.
*   **Step 1.3: User Stories & Acceptance Criteria**
    *   **Action:** For each page identified in Step 1.2, generate detailed user stories.
    *   **Format:** `As a [user], I want to [action] so that [benefit].`
    *   **Output:** Generate `03_user_stories_and_criteria.txt`.
*   **Step 1.4: Architecture & Text-based Wireframes**
    *   **Action:** Define Angular component hierarchy, modular structure (Core, Shared, Features), and services. Create text-based UI mockups/wireframes.
    *   **Output:** Generate `04_architecture_and_wireframes.txt`.

---

## Phase 2: Environment Setup & TDD Configuration
**Goal:** Create a robust, modular foundation strictly adhering to TDD.

*   **Step 2.1: Initialize Angular Project**
    *   **Action:** Execute `ng new frontend --routing --style=css --strict`.
*   **Step 2.2: Configure Testing Framework**
    *   **Action:** Ensure Jasmine/Karma (or Jest) is properly configured for unit testing. Setup code coverage reporting.
*   **Step 2.3: Scaffold Architecture**
    *   **Action:** Create base folder structure inside `src/app/` (e.g., `/core`, `/shared`, `/features`).

---

## Phase 3: TDD Front-End Implementation (Iterative Feature Loop)
**Goal:** Build components step-by-step. For EVERY feature, Claude MUST follow this strict TDD loop:
1.  **Write Unit Test:** Define the component/service behavior based on Acceptance Criteria.
2.  **Run Test:** Ensure it fails (Red).
3.  **Write Code:** Implement TypeScript, HTML, CSS.
4.  **Run Test:** Ensure it passes (Green).
5.  **Refactor:** Clean up code, ensure modularity.

*   **Step 3.1: Core Services & Interceptors**
    *   **Action:** Implement Auth Service, Error Handling, and HTTP Interceptors. Test API mocking.
*   **Step 3.2: Shared Components**
    *   **Action:** Implement reusable UI elements (buttons, navbars, modals, tables) using HTML/CSS. 
*   **Step 3.3: Feature Modules**
    *   **Action:** Build out specific pages (Dashboard, Transfers) as mapped in Step 1.2. 

---

## Phase 4: Backend Integration
**Goal:** Connect the TDD-validated front-end to the live Java/PostgreSQL backend.

*   **Step 4.1: Environment Configuration**
    *   **Action:** Setup `environment.ts` and `environment.development.ts` with backend API URLs. Setup `proxy.conf.json` for local CORS bypass.
*   **Step 4.2: API Wiring**
    *   **Action:** Replace any mocked data in Angular Services with actual `HttpClient` calls to the Java backend.
*   **Step 4.3: Integration Testing**
    *   **Action:** Run tests to ensure data models match exactly between Angular interfaces and Java DTOs.

---

## Phase 5: End-to-End (E2E) Testing & Polish
**Goal:** Ensure the entire application works seamlessly from user click to database save.

*   **Step 5.1: E2E Setup**
    *   **Action:** Install and configure Cypress (or Angular's preferred E2E tool).
*   **Step 5.2: User Journey Tests**
    *   **Action:** Write E2E tests based on the User Stories generated in Phase 1. Validate complete flows (e.g., Login -> View Balance -> Transfer Funds -> Logout).
*   **Step 5.3: Final Review**
    *   **Action:** Check for responsiveness, CSS polish, and accessibility.

---
## ⚡ How to use this file with Claude Code:
1. Save this file to the root of your project workspace.
2. Open your terminal and start Claude Code.
3. Prompt: *"Claude, read `00_Master_Frontend_Plan_For_Claude.md`. Let's begin Phase 1, Step 1.1. Analyze my backend Java code and generate the API specs."*