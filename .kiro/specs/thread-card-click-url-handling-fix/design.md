# Thread Card Click URL Handling Fix Bugfix Design

## Overview

This bugfix addresses multiple UI interaction issues in the Android app affecting thread navigation, link handling, and contextual menus. The fix targets 5 specific bug conditions: thread card click non-responsiveness, URL non-interactivity in posts, missing post three-dot menus, deleted post interaction blocking, and missing thread list three-dot menus. The design focuses on practical Android/Compose implementations that solve each bug while preserving existing functionality.

## Glossary

- **Bug_Condition (C)**: The condition that triggers each bug - when specific UI interactions fail
- **Property (P)**: The desired behavior when bugs are fixed - proper navigation, link handling, and menu functionality
- **Preservation**: Existing functionality that must remain unchanged by the fix
- **ThreadCard**: The composable component in `ThreadListScreen.kt` that displays thread information
- **ChanHtmlText**: The composable component in `ChanHtmlText.kt` that renders HTML-formatted post comments with link detection
- **PostCard**: The composable component in `ThreadDetailScreen.kt` that displays individual post information
- **SavedThreadCard**: The composable component in `SavedScreen.kt` that displays saved thread information
- **ChanCard**: The reusable card component in `ChanCard.kt` that provides clickable card styling

## Bug Details

### Bug Condition 1: Thread Card Click Non-Responsiveness

The bug manifests when a user taps on any thread card in ThreadListScreen or SavedScreen. The system does not respond with navigation or visual feedback.

**Formal Specification:**
```
FUNCTION isBugCondition1(input)
  INPUT: input of type ThreadCardClickEvent
  OUTPUT: boolean
  
  RETURN input.screen ∈ {"ThreadListScreen", "SavedScreen"} 
         AND input.cardType = "threadCard"
         AND NOT hasClickHandling(input.cardComponent)
END FUNCTION
```

### Bug Condition 2: URL Non-Interactivity in Posts

The bug manifests when URLs appear in post comments (via ChanHtmlText component). The system displays them as plain text without clickable link styling or interaction.

**Formal Specification:**
```
FUNCTION isBugCondition2(input)
  INPUT: input of type PostCommentContent
  OUTPUT: boolean
  
  RETURN input.containsUrl = true 
         AND input.displayMode = "plainText"
         AND NOT hasLinkHandling(input)
END FUNCTION
```

### Bug Condition 3: Missing Post Three-Dot Menu

The bug manifests when a user views the ThreadDetailScreen. The system does not provide a three-dot menu on individual post cards for post-specific actions.

**Formal Specification:**
```
FUNCTION isBugCondition3(input)
  INPUT: input of type PostCardContext
  OUTPUT: boolean
  
  RETURN input.screen = "ThreadDetailScreen" 
         AND input.cardComponent = "PostCard"
         AND input.menuOptions = ∅
END FUNCTION
```

### Bug Condition 4: Deleted Post Interaction Blocking

The bug manifests when a user views a saved thread with deleted posts. The system prevents interaction with deleted posts (cannot click to view replies or read replied text).

**Formal Specification:**
```
FUNCTION isBugCondition4(input)
  INPUT: input of type DeletedPostInteraction
  OUTPUT: boolean
  
  RETURN input.postStatus = "deleted" 
         AND input.screen = "SavedScreen"
         AND input.interactionAllowed = false
END FUNCTION
```

### Bug Condition 5: Missing Thread List Three-Dot Menu

The bug manifests when a user views ThreadListScreen or SavedScreen. The system does not provide a three-dot menu on the thread list cards for thread-specific actions.

**Formal Specification:**
```
FUNCTION isBugCondition5(input)
  INPUT: input of type ThreadListContext
  OUTPUT: boolean
  
  RETURN input.screen ∈ {"ThreadListScreen", "SavedScreen"} 
         AND input.cardType = "threadCard"
         AND input.menuOptions = ∅
END FUNCTION
```

### Examples

**Bug 1 Example**: User taps a thread card showing "No. 1234567" in ThreadListScreen - Expected: Navigates to thread detail view with ripple effect. Actual: No response.

**Bug 2 Example**: Post contains "Check out https://example.com" - Expected: Underlined blue link opens in browser when tapped. Actual: Plain text with no interaction.

**Bug 3 Example**: Viewing thread #1234567 in ThreadDetailScreen - Expected: Three-dot menu on each post with "Copy Post Link", "Copy Text", "Open Post Link", "Share Post". Actual: No menu visible.

**Bug 4 Example**: Saved thread contains deleted post #1234568 - Expected: Can click to view replies and read replied text. Actual: Interaction blocked, cannot access replies.

**Bug 5 Example**: Thread card in SavedScreen - Expected: Three-dot menu with thread options. Actual: No menu available.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Images in thread cards must continue to open the image viewer with proper navigation
- Reply links (>>postNo) in post comments must continue to navigate to referenced posts
- Three-dot menu that exists in ThreadCard (ThreadListScreen) must continue to provide thread-level options
- Non-deleted posts must continue to show reply buttons and reply count indicators
- Saved threads must continue to show polling status, unsave functionality, and saved date information

**Scope:**
All inputs that do NOT involve the specific bug conditions should be completely unaffected by this fix. This includes:
- Mouse clicks on images in thread cards
- Reply link navigation in post comments
- Existing three-dot menu functionality in ThreadListScreen
- Non-deleted post interactions
- Saved thread display and polling features

## Hypothesized Root Cause

Based on code analysis, the most likely issues are:

1. **Missing onClick Parameter**: ThreadCard and SavedThreadCard components may not be passing onClick handlers to ChanCard
   - ChanCard component requires `onClick: (() -> Unit)? = null` parameter
   - ThreadCard in ThreadListScreen.kt line 1067 calls `onClick = { onThreadClick(thread) }`
   - SavedThreadCard in SavedScreen.kt line 349 uses `combinedClickable` but may have incorrect setup

2. **URL Detection Issue in ChanHtmlText**: The component may not be detecting or styling plain text URLs properly
   - ChanHtmlText.kt has `UrlRegex = Regex("""https?://[^\s<>()]+[^\s<>().,!?:;]""")`
   - The onClick handler at line 116 checks for URL_TAG annotations
   - URL styling may not be applied to plain text URLs

3. **Conditional Menu Rendering**: PostCard may have conditional rendering logic preventing menu display
   - PostCard in ThreadDetailScreen.kt line 667 has `showOverflowMenu: Boolean = false` parameter
   - Menu rendering is conditional: `if (showOverflowMenu) { ... } else { Icon(...) }`
   - ThreadDetailScreen calls PostCard with `showOverflowMenu = true` at line 394

4. **Deleted Post Interaction Block**: PostCard may be blocking interactions for deleted posts
   - PostCard has `allowDeletedInteractions: Boolean = false` parameter
   - `onReplyClick` is set to null when `post.isDeleted && !allowDeletedInteractions`
   - SavedScreen may not be passing `allowDeletedInteractions = true`

5. **Missing Menu Implementation**: ThreadCard in ThreadListScreen already has menu, but SavedThreadCard may be missing it
   - ThreadCard in ThreadListScreen.kt lines 1070-1115 has three-dot menu implementation
   - SavedThreadCard in SavedScreen.kt uses `combinedClickable` for long-press menu but may not have visible three-dot menu

## Correctness Properties

Property 1: Bug Condition - Thread Card Click Responsiveness

_For any_ thread card displayed in ThreadListScreen or SavedScreen, when a user taps on the card, the fixed system SHALL navigate to the corresponding thread detail view with proper visual feedback (ripple effect).

**Validates: Requirements 2.1**

Property 2: Bug Condition - URL Interactivity in Posts

_For any_ URL appearing in post comments via ChanHtmlText component, the fixed system SHALL display it as an underlined blue link and make it clickable to open in an external browser.

**Validates: Requirements 2.2**

Property 3: Bug Condition - Post Three-Dot Menu Presence

_For any_ post card displayed in ThreadDetailScreen, the fixed system SHALL display a three-dot menu with options: copy post link, copy text, open post link, share post.

**Validates: Requirements 2.3**

Property 4: Bug Condition - Deleted Post Interaction

_For any_ deleted post displayed in SavedScreen, the fixed system SHALL allow clicking on the post to view its replies and read the replied text.

**Validates: Requirements 2.4**

Property 5: Bug Condition - Thread List Three-Dot Menu

_For any_ thread card displayed in ThreadListScreen or SavedScreen, the fixed system SHALL display a three-dot menu with appropriate thread-specific actions.

**Validates: Requirements 2.5**

Property 6: Preservation - Existing Functionality

_For any_ input where the bug conditions do NOT hold (non-buggy interactions), the fixed system SHALL produce exactly the same behavior as the original system, preserving all existing functionality.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

**Bug 1 Fix - Thread Card Click Handling:**

File: `app/src/main/java/com/chan/mimi/ui/screens/threads/ThreadListScreen.kt`

1. **ThreadCard Component**: Ensure onClick handler is properly passed to ChanCard
   - Current: Line 1067 shows `onClick = { onThreadClick(thread) }`
   - Issue: ChanCard should receive this onClick parameter
   - Fix: Verify ChanCard is called with `onClick = { onThreadClick(thread) }`

2. **SavedThreadCard Component**: Fix click handling in SavedScreen
   - Current: Line 349 uses `combinedClickable(onClick = onClick, onLongClick = { showMenu = true })`
   - Issue: ChanCard is called with `onClick = null` at line 352
   - Fix: Remove `onClick = null` from ChanCard call and rely on combinedClickable

**Bug 2 Fix - URL Handling in ChanHtmlText:**

File: `app/src/main/java/com/chan/mimi/ui/components/ChanHtmlText.kt`

1. **URL Detection Enhancement**: Improve URL regex to catch more URL patterns
   - Current regex may miss some URL formats
   - Add support for URLs without protocol, common URL patterns

2. **Link Styling Verification**: Ensure URL annotations are properly added
   - Check that `appendTextWithUrlAnnotations` function adds URL_TAG annotations
   - Verify link styling (underlined blue) is applied

3. **Click Handler Testing**: Test that onClick handler opens URLs in browser
   - Current handler at lines 116-135 should handle URL_TAG annotations
   - Test with various URL formats

**Bug 3 Fix - Post Three-Dot Menu:**

File: `app/src/main/java/com/chan/mimi/ui/screens/threads/ThreadDetailScreen.kt`

1. **PostCard Parameter Verification**: Ensure `showOverflowMenu = true` is passed
   - Current: Line 394 calls PostCard with `showOverflowMenu = true`
   - Verify this parameter is correctly received by PostCard

2. **Menu Rendering Check**: Ensure menu renders when `showOverflowMenu = true`
   - PostCard lines 716-728 conditionally render menu based on `showOverflowMenu`
   - Test that menu appears with correct options

**Bug 4 Fix - Deleted Post Interaction:**

File: `app/src/main/java/com/chan/mimi/ui/screens/saved/SavedThreadDetailScreen.kt`

1. **Allow Deleted Interactions**: Pass `allowDeletedInteractions = true` to PostCard
   - When displaying saved threads with deleted posts
   - Ensure `onReplyClick` is not null for deleted posts

2. **Interaction Restoration**: Enable clicking on deleted posts
   - Remove blocking conditions for deleted post interactions
   - Allow reply viewing and text reading for deleted posts

**Bug 5 Fix - Thread List Three-Dot Menu:**

File: `app/src/main/java/com/chan/mimi/ui/screens/saved/SavedScreen.kt`

1. **Add Visible Three-Dot Menu**: Implement visible menu icon on SavedThreadCard
   - ThreadCard has visible three-dot menu (lines 1070-1115)
   - SavedThreadCard currently uses long-press menu only
   - Add visible IconButton with DropdownMenu similar to ThreadCard

2. **Menu Options Consistency**: Provide same thread-level options
   - Copy thread link, copy text, open thread link, share thread
   - Add save/unsave functionality

### Implementation Details

**Component Modifications:**
1. `ThreadListScreen.kt`: Verify ThreadCard onClick handling
2. `SavedScreen.kt`: Fix SavedThreadCard click handling and add visible three-dot menu
3. `ChanHtmlText.kt`: Enhance URL detection and styling
4. `ThreadDetailScreen.kt`: Ensure PostCard menu renders correctly
5. `SavedThreadDetailScreen.kt`: Enable deleted post interactions

**Navigation Fixes:**
- Ensure all card clicks provide visual feedback (ripple via ChanCard)
- Proper navigation to thread detail views
- Maintain existing image viewer navigation

**URL Handling:**
- Improved regex pattern for URL detection
- Proper link styling (underlined blue)
- Browser intent handling for clicked URLs

**Three-Dot Menu Implementations:**
- Consistent menu design across components
- Appropriate options per context (post vs thread)
- Proper event handling for menu actions

**Deleted Post Handling:**
- Enable interactions for deleted posts in saved threads
- Allow reply viewing and text reading
- Maintain visual distinction for deleted content

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bugs on unfixed code, then verify the fixes work correctly and preserve existing behavior.

### Exploratory Bug Condition Checking

**Goal**: Surface counterexamples that demonstrate the bugs BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write tests that simulate user interactions for each bug condition and assert expected behavior. Run these tests on the UNFIXED code to observe failures and understand the root cause.

**Test Cases**:
1. **Thread Card Click Test**: Simulate tapping thread card in ThreadListScreen (will fail on unfixed code)
2. **URL Click Test**: Simulate tapping URL in post comment (will fail on unfixed code)
3. **Post Menu Test**: Check for three-dot menu on PostCard in ThreadDetailScreen (will fail on unfixed code)
4. **Deleted Post Interaction Test**: Simulate clicking deleted post in SavedScreen (will fail on unfixed code)
5. **Thread List Menu Test**: Check for three-dot menu on SavedThreadCard (will fail on unfixed code)

**Expected Counterexamples**:
- Thread card taps produce no navigation
- URL taps produce no browser intent
- Post cards show no three-dot menu
- Deleted posts block interactions
- Saved thread cards show no three-dot menu

### Fix Checking

**Goal**: Verify that for all inputs where the bug conditions hold, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition1(input) DO
  result := fixedThreadCardClick(input)
  ASSERT result = "navigationToThreadDetail" AND hasVisualFeedback(result)
END FOR

FOR ALL input WHERE isBugCondition2(input) DO
  result := fixedUrlHandling(input)
  ASSERT result.displayMode = "clickableLink" AND result.style = "underlinedBlue" AND result.action = "openInBrowser"
END FOR

FOR ALL input WHERE isBugCondition3(input) DO
  result := fixedPostCard(input)
  ASSERT result.menuOptions = {"copyPostLink", "copyText", "openPostLink", "sharePost"}
END FOR

FOR ALL input WHERE isBugCondition4(input) DO
  result := fixedDeletedPostInteraction(input)
  ASSERT result.interactionAllowed = true AND result.canViewReplies = true AND result.canReadRepliedText = true
END FOR

FOR ALL input WHERE isBugCondition5(input) DO
  result := fixedThreadListCard(input)
  ASSERT result.menuOptions ≠ ∅ AND result.menuOptions ⊆ {"copyThreadLink", "copyText", "openThreadLink", "shareThread", "saveThread"}
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug conditions do NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT (isBugCondition1(input) OR isBugCondition2(input) OR isBugCondition3(input) OR isBugCondition4(input) OR isBugCondition5(input)) DO
  ASSERT originalSystem(input) = fixedSystem(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because it generates many test cases automatically across the input domain and catches edge cases that manual unit tests might miss.

**Test Plan**: Observe behavior on UNFIXED code first for non-bug interactions, then write property-based tests capturing that behavior.

**Test Cases**:
1. **Image Click Preservation**: Verify clicking images in thread cards continues to open image viewer
2. **Reply Link Preservation**: Verify >>postNo links continue to navigate to referenced posts
3. **Existing Menu Preservation**: Verify existing three-dot menu in ThreadListScreen continues working
4. **Non-Deleted Post Preservation**: Verify reply buttons and count indicators work on non-deleted posts
5. **Saved Thread Features Preservation**: Verify polling status, unsave functionality, and saved date display continue working

### Unit Tests

- Test thread card click handling in ThreadListScreen and SavedScreen
- Test URL detection and clicking in ChanHtmlText with various URL formats
- Test PostCard three-dot menu rendering with `showOverflowMenu = true`
- Test deleted post interaction enabling in SavedThreadDetailScreen
- Test SavedThreadCard three-dot menu implementation
- Test that existing functionality (images, reply links, etc.) continues working

### Property-Based Tests

- Generate random thread configurations and verify card click navigation
- Generate random post comments with URLs and verify link detection and styling
- Generate random thread/post states and verify menu presence/absence based on context
- Generate random saved thread states with deleted posts and verify interaction availability
- Generate random UI states and verify preservation of non-buggy behavior

### Integration Tests

- Test full navigation flow: thread list → thread detail → post interactions
- Test URL handling flow: post with URL → tap → browser opens
- Test menu functionality flow: open menu → select option → expected action
- Test saved thread flow: save thread → view saved → interact with deleted posts
- Test cross-screen consistency: thread cards behave consistently across ThreadListScreen and SavedScreen
