GeoStaff
GeoStaff is a real-time Android application built to manage field employees, assign tasks, and track attendance. It uses on-device Artificial Intelligence and GPS geo-fencing to verify work and location, keeping teams synchronized without relying on expensive cloud processing.

Key Features
AI Proof Validator: Uses Google ML Kit to scan employee proof-of-work photos completely offline. It automatically checks if the uploaded photo matches the assigned task (e.g., verifying a picture of a laptop for a tech repair task).

Smart Task Priority: Analyzes task descriptions typed by the Admin. If words like "urgent" or "ASAP" are detected, the app automatically flags the task as High Priority and moves it to the top of the employee's list.

Geo-Fenced Attendance: Uses Google Play Location Services to verify an employee's exact GPS coordinates. Employees are restricted from clocking in unless they are within a 200-meter radius of the designated office location.

Real-Time Notifications: Integrates the Firebase Cloud Messaging (FCM V1) API to send instant push notifications to specific employees the moment a new task is assigned to them.

Role-Based Portals: Distinct interfaces for Admins (to view attendance history, assign tasks, and check proof) and Employees (to view tasks, upload photos, and clock in).

Technologies Used
Language: Kotlin

UI Toolkit: Jetpack Compose

Backend: Firebase (Firestore, Authentication)

Push Notifications: Firebase Cloud Messaging (FCM V1 API)

Machine Learning: Google ML Kit (Image Labeling)

Location & Hardware: Google Play Services Location API, CameraX
