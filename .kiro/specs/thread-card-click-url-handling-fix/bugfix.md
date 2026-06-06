# Bugfix Requirements Document

## Introduction

This bugfix addresses multiple UI interaction issues in the Android app affecting thread navigation, link handling, and contextual menus. Core functionality like clicking thread cards to open threads is broken, URLs in posts are not interactive, and three-dot menu options are missing from key screens. These issues hinder user navigation and interaction with content.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN a user taps on any thread card in ThreadListScreen or SavedScreen THEN the system does not respond (no navigation, no visual feedback)

1.2 WHEN URLs appear in post comments (via ChanHtmlText component) THEN the system displays them as plain text without clickable link styling or interaction

1.3 WHEN a user views the ThreadDetailScreen THEN the system does not provide a three-dot menu on individual post cards for post-specific actions (copy post link, copy text, open post link, share post)

1.4 WHEN a user views a saved thread with deleted posts THEN the system prevents interaction with deleted posts (cannot click to view replies or read replied text)

1.5 WHEN a user views ThreadListScreen or SavedScreen THEN the system does not provide a three-dot menu on the thread list cards for thread-specific actions

### Expected Behavior (Correct)

2.1 WHEN a user taps on any thread card in ThreadListScreen or SavedScreen THEN the system SHALL navigate to the corresponding thread detail view with proper visual feedback (ripple effect)

2.2 WHEN URLs appear in post comments THEN the system SHALL display them as underlined blue links and make them clickable to open in an external browser

2.3 WHEN a user views the ThreadDetailScreen THEN the system SHALL display a three-dot menu on each post card with options: copy post link, copy text, open post link, share post

2.4 WHEN a user views a saved thread with deleted posts THEN the system SHALL allow clicking on deleted posts to read their replies and click on deleted replies to read the replied text

2.5 WHEN a user views ThreadListScreen or SavedScreen THEN the system SHALL display a three-dot menu on each thread card with appropriate thread-specific actions

### Unchanged Behavior (Regression Prevention)

3.1 WHEN images in thread cards are tapped THEN the system SHALL CONTINUE TO open the image viewer with proper navigation

3.2 WHEN reply links (>>postNo) in post comments are tapped THEN the system SHALL CONTINUE TO navigate to the referenced post

3.3 WHEN the three-dot menu exists in ThreadCard (ThreadListScreen) THEN the system SHALL CONTINUE TO provide thread-level options (copy thread link, copy text, open thread link, share thread, add to saved)

3.4 WHEN non-deleted posts are displayed THEN the system SHALL CONTINUE TO show reply buttons and reply count indicators

3.5 WHEN saved threads are displayed THEN the system SHALL CONTINUE TO show polling status, unsave functionality, and saved date information


## Bug Condition Derivation

Based on the requirements above, here are the bug conditions and properties:

### Bug Condition 1: Thread Card Click Non-Responsiveness
```pascal
FUNCTION isBugCondition1(X)
  INPUT: X of type ThreadCardClickEvent
  OUTPUT: boolean
  
  RETURN X.screen ∈ {ThreadListScreen, SavedScreen} AND X.cardType = "threadCard"
END FUNCTION

// Property: Fix Checking - Thread Card Click Handling
FOR ALL X WHERE isBugCondition1(X) DO
  result ← F'(X)
  ASSERT result = "navigationToThreadDetail" AND hasVisualFeedback(result)
END FOR
```

### Bug Condition 2: URL Non-Interactivity in Posts
```pascal
FUNCTION isBugCondition2(X)
  INPUT: X of type PostCommentContent
  OUTPUT: boolean
  
  RETURN X.containsUrl = true AND X.displayMode = "plainText"
END FUNCTION

// Property: Fix Checking - URL Link Handling
FOR ALL X WHERE isBugCondition2(X) DO
  result ← F'(X)
  ASSERT result.displayMode = "clickableLink" AND result.style = "underlinedBlue" AND result.action = "openInBrowser"
END FOR
```

### Bug Condition 3: Missing Post Three-Dot Menu
```pascal
FUNCTION isBugCondition3(X)
  INPUT: X of type PostCardContext
  OUTPUT: boolean
  
  RETURN X.screen = "ThreadDetailScreen" AND X.menuOptions = ∅
END FUNCTION

// Property: Fix Checking - Post Context Menu
FOR ALL X WHERE isBugCondition3(X) DO
  result ← F'(X)
  ASSERT result.menuOptions = {"copyPostLink", "copyText", "openPostLink", "sharePost"}
END FOR
```

### Bug Condition 4: Deleted Post Interaction Blocking
```pascal
FUNCTION isBugCondition4(X)
  INPUT: X of type DeletedPostInteraction
  OUTPUT: boolean
  
  RETURN X.postStatus = "deleted" AND X.screen = "SavedScreen" AND X.interactionAllowed = false
END FUNCTION

// Property: Fix Checking - Deleted Post Interaction
FOR ALL X WHERE isBugCondition4(X) DO
  result ← F'(X)
  ASSERT result.interactionAllowed = true AND result.canViewReplies = true AND result.canReadRepliedText = true
END FOR
```

### Bug Condition 5: Missing Thread List Three-Dot Menu
```pascal
FUNCTION isBugCondition5(X)
  INPUT: X of type ThreadListContext
  OUTPUT: boolean
  
  RETURN X.screen ∈ {"ThreadListScreen", "SavedScreen"} AND X.menuOptions = ∅
END FUNCTION

// Property: Fix Checking - Thread List Context Menu
FOR ALL X WHERE isBugCondition5(X) DO
  result ← F'(X)
  ASSERT result.menuOptions ≠ ∅ AND result.menuOptions ⊆ {"copyThreadLink", "copyText", "openThreadLink", "shareThread", "saveThread"}
END FOR
```

### Preservation Goal
For all inputs where the bug conditions are NOT met, the fixed system should behave identically to the original system:

```pascal
// Property: Preservation Checking
FOR ALL X WHERE NOT (isBugCondition1(X) OR isBugCondition2(X) OR isBugCondition3(X) OR isBugCondition4(X) OR isBugCondition5(X)) DO
  ASSERT F(X) = F'(X)
END FOR
```

**Key Definitions:**
- **F**: Original (unfixed) system - the code as it exists before the fix
- **F'**: Fixed system - the code after applying all fixes
- **X**: Input representing user interactions and UI contexts
- **C(X)**: Bug condition functions that identify problematic states
- **P(result)**: Properties defining correct behavior for buggy inputs