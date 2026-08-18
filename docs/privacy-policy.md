# Privacy Policy for HamGhadam (همقدم)

**Effective Date:** August 18, 2026  
**App Version:** V1.2.0  
**Domain:** `https://hamghadam.ba4b0d.ir`  
**Contact:** `privacy@hamghadam.ba4b0d.ir` / `support@hamghadam.ba4b0d.ir`

---

## 1. Overview
HamGhadam ("همقدم") is a privacy-conscious social fitness app designed to track step count, sleep duration, and heart rate metrics, participate in peer step challenges, and engage with friends. We are committed to transparency in how we collect, store, process, and protect your personal and health data.

---

## 2. Information We Collect & How We Use It

### 2.1 Personal & Profile Information
- **Account Identity**: When registering via Email or Google Sign-In, we collect your email address and display name for account authentication and identification.
- **Profile Data**: You may optionally provide a bio and upload a custom profile picture (avatar).
- **Google Sign-In Data**: When signing in with Google, we receive your email address, display name, Google ID (`sub`), and default profile picture. We adhere strictly to the **Google API Services User Data Policy**, including the **Limited Use** requirements. Your Google user data is strictly used for authentication and profile setup; it is never shared with third parties or ad networks.

### 2.2 Health & Fitness Data (Health Connect)
- **Fitness Data Collected**: Step count, sleep duration, and heart rate readings obtained via Android Health Connect API upon your explicit permission.
- **Purpose**: Strictly used for core app features: calculating daily activity summaries, rendering personal health trends, and scoring peer challenge leaderboards.
- **No Third-Party Sharing**: Health data is never sold, transferred, or disclosed to advertising platforms, data brokers, or credit agencies.

### 2.3 Social Network & Friends Data
- **Friends Graph**: When you send or accept friend requests, we maintain a friendship link between your account and your friend's account.
- **Social Data Visibility**: Your display name, avatar, bio, and today's step count are visible exclusively to your confirmed friends and participants in step challenges you join.
- **User-Generated Content (UGC)**: Avatars uploaded by users are stored securely over HTTPS on server storage. Users must not upload inappropriate, illegal, or copyright-violating images.

---

## 3. Play Console Data Safety Declarations

| Data Category | Specific Data | Collection Status | Sharing Status | Primary Purpose | Security Measure |
|---|---|---|---|---|---|
| **Personal Info** | Name / Display Name | Collected (Optional) | Not Shared | Account Management, Social Leaderboard | TLS Encrypted in Transit |
| **Personal Info** | Email Address | Collected (Required for Email / Optional for Google) | Not Shared | Account Auth & Security | TLS Encrypted in Transit |
| **Photos & Videos** | Photos / UGC Avatars | Collected (Optional) | Not Shared | Profile Customization | TLS Encrypted in Transit |
| **Contacts / Social** | Friends List / Social Graph | Collected (Optional) | Not Shared | App Functionality, Social Challenges | TLS Encrypted in Transit |
| **Fitness & Health** | Steps, Sleep, Heart Rate | Collected (Optional via Health Connect) | Not Shared | Core App Functionality, Challenges | TLS Encrypted in Transit |

---

## 4. Data Security & Storage
- **Encryption**: All data exchanged between the HamGhadam mobile application and our backend server is encrypted in transit using Transport Layer Security (TLS 1.3/HTTPS).
- **Static Assets**: Uploaded avatars are sanitized (UUID v4 filenames, validated binary image signatures) and served securely over HTTPS.

---

## 5. Account & Data Deletion (Right to Erasure)
Google Play Policy requires that users can request deletion of their account and associated data. HamGhadam provides two methods for account deletion:

1. **In-App Deletion**:
   - Navigate to **Profile > Settings > Delete Account** or `DELETE /api/v1/users/me`.
   - Executing account deletion immediately and permanently purges your user profile, email, authentication tokens, avatar image files, friendship links, and historical health scores from our database.
2. **Web Request Deletion Page**:
   - If you cannot access the mobile application, you can submit a deletion request via our web page at `https://hamghadam.ba4b0d.ir/privacy/delete-account` or by emailing `delete-my-account@hamghadam.ba4b0d.ir` from your registered email address.

---

## 6. Contact Us
If you have any questions or privacy concerns, please contact our Data Protection team at:
- **Email**: `privacy@hamghadam.ba4b0d.ir`
- **Web**: `https://hamghadam.ba4b0d.ir/privacy`
