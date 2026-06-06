# Implementation Plan

- [ ] 1. Write bug condition exploration tests (BEFORE implementing fix)
  
  - [ ] 1.1 Thread card click non-responsiveness exploration test
    - **Property 1: Bug Condition** - Thread Card Click Non-Responsiveness
    - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
    - **DO NOT attempt to fix the test or the code when it fails**
    - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
    - **GOAL**: Surface counterexamples that demonstrate the bug exists
    - **Scoped PBT Approach**: For deterministic bugs, scope the property to the concrete failing case(s) to ensure reproducibility
    - Test that clicking any thread card in ThreadListScreen or SavedScreen produces no navigation response
    - Verify that isBugCondition1(input) holds for thread cards in these screens
    - Run test on UNFIXED code
    - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
    - Document counterexamples found to understand root cause (e.g., "Thread card tap in ThreadListScreen produces no navigation")
    - Mark task complete when test is written, run, and failure is documented
    - _Requirements: 1.1, 2.1_
    - _Bug_Condition: isBugCondition1(input) where input.screen ∈ {ThreadListScreen, SavedScreen} AND input.cardType = "threadCard"_
    - _Expected_Behavior: Expected to navigate to thread detail view with visual feedback_

  - [ ] 1.2 URL non-interactivity exploration test
    - **Property 1: Bug Condition** - URL Non-Interactivity in Posts
    - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
    - **DO NOT attempt to fix the test or the code when it fails**
    - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
    - **GOAL**: Surface counterexamples that demonstrate the bug exists
    - **Scoped PBT Approach**: For deterministic bugs, scope the property to the concrete failing case(s) to ensure reproducibility
    - Test that URLs in post comments via ChanHtmlText component are displayed as plain text without clickable link styling
    - Verify that isBugCondition2(input) holds for posts containing URLs
    - Run test on UNFIXED code
    - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
    - Document counterexamples found (e.g., "URL https://example.com appears as plain text without link styling")
    - Mark task complete when test is written, run, and failure is documented
    - _Requirements: 1.2, 2.2_
    - _Bug_Condition: isBugCondition2(input) where input.containsUrl = true AND input.displayMode = "plainText"_
    - _Expected_Behavior: Expected to display as underlined blue clickable links opening in browser_

  - [ ] 1.3 Missing post three-dot menu exploration test
    - **Property 1: Bug Condition** - Missing Post Three-Dot Menu
    - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
    - **DO NOT attempt to fix the test or the code when it fails**
    - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
    - **GOAL**: Surface counterexamples that demonstrate the bug exists
    - **Scoped PBT Approach**: For deterministic bugs, scope the property to the concrete failing case(s) to ensure reproducibility
    - Test that PostCard components in ThreadDetailScreen have no visible three-dot menu
    - Verify that isBugCondition3(input) holds for post cards in ThreadDetailScreen
    - Run test on UNFIXED code
    - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
    - Document counterexamples found (e.g., "Post card shows no three-dot menu for post-specific actions")
    - Mark task complete when test is written, run, and failure is documented
    - _Requirements: 1.3, 2.3_
    - _Bug_Condition: isBugCondition3(input) where input.screen = "ThreadDetailScreen" AND input.cardComponent = "PostCard" AND input.menuOptions = ∅_
    - _Expected_Behavior: Expected to show three-dot menu with options: copy post link, copy text, open post link, share post_

  - [ ] 1.4 Deleted post interaction blocking exploration test
    - **Property 1: Bug Condition** - Deleted Post Interaction Blocking
    - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
    - **DO NOT attempt to fix the test or the code when it fails**
    - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
    - **GOAL**: Surface counterexamples that demonstrate the bug exists
    - **Scoped PBT Approach**: For deterministic bugs, scope the property to the concrete failing case(s) to ensure reproducibility
    - Test that deleted posts in SavedScreen block interactions (cannot click to view replies or read replied text)
    - Verify that isBugCondition4(input) holds for deleted posts in SavedScreen
    - Run test on UNFIXED code
    - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
    - Document counterexamples found (e.g., "Deleted post blocks clicking to view replies")
    - Mark task complete when test is written, run, and failure is documented
    - _Requirements: 1.4, 2.4_
    - _Bug_Condition: isBugCondition4(input) where input.postStatus = "deleted" AND input.screen = "SavedScreen" AND input.interactionAllowed = false_
    - _Expected_Behavior: Expected to allow clicking on deleted posts to view replies and read replied text_

  - [ ] 1.5 Missing thread list three-dot menu exploration test
    - **Property 1: Bug Condition** - Missing Thread List Three-Dot Menu
    - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
    - **DO NOT attempt to fix the test or the code when it fails**
    - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
    - **GOAL**: Surface counterexamples that demonstrate the bug exists
    - **Scoped PBT Approach**: For deterministic bugs, scope the property to the concrete failing case(s) to ensure reproducibility
    - Test that thread cards in ThreadListScreen and SavedScreen have no visible three-dot menu for thread-specific actions
    - Verify that isBugCondition5(input) holds for thread cards in these screens
    - Run test on UNFIXED code
    - **EXPECTED OUTCOME**: Test FAILS (this is correct - it proves the bug exists)
    - Document counterexamples found (e.g., "Thread card shows no three-dot menu for thread actions")
    - Mark task complete when test is written, run, and failure is documented
    - _Requirements: 1.5, 2.5_
    - _Bug_Condition: isBugCondition5(input) where input.screen ∈ {"ThreadListScreen", "SavedScreen"} AND input.cardType = "threadCard" AND input.menuOptions = ∅_
    - _Expected_Behavior: Expected to show three-dot menu with thread-specific actions_

- [ ] 2. Write preservation property tests (BEFORE implementing fix)
  
  - [ ] 2.1 Image click preservation property test
    - **Property 2: Preservation** - Image Click Navigation
    - **IMPORTANT**: Follow observation-first methodology
    - Observe behavior on UNFIXED code: clicking images in thread cards opens image viewer with proper navigation
    - Write property-based test: for all thread cards containing images, clicking images continues to open image viewer
    - Verify test passes on UNFIXED code
    - _Requirements: 3.1_

  - [ ] 2.2 Reply link preservation property test
    - **Property 2: Preservation** - Reply Link Navigation
    - **IMPORTANT**: Follow observation-first methodology
    - Observe behavior on UNFIXED code: reply links (>>postNo) in post comments navigate to referenced posts
    - Write property-based test: for all >>postNo links in post comments, clicking continues to navigate to referenced posts
    - Verify test passes on UNFIXED code
    - _Requirements: 3.2_

  - [ ] 2.3 Existing menu preservation property test
    - **Property 2: Preservation** - Existing Thread Card Menu
    - **IMPORTANT**: Follow observation-first methodology
    - Observe behavior on UNFIXED code: ThreadCard in ThreadListScreen has working three-dot menu with thread-level options
    - Write property-based test: for all thread cards in ThreadListScreen, existing three-dot menu continues to provide thread-level options (copy thread link, copy text, open thread link, share thread, add to saved)
    - Verify test passes on UNFIXED code
    - _Requirements: 3.3_

  - [ ] 2.4 Non-deleted post preservation property test
    - **Property 2: Preservation** - Non-Deleted Post Interactions
    - **IMPORTANT**: Follow observation-first methodology
    - Observe behavior on UNFIXED code: non-deleted posts show reply buttons and reply count indicators
    - Write property-based test: for all non-deleted posts, reply buttons and count indicators continue to work
    - Verify test passes on UNFIXED code
    - _Requirements: 3.4_

  - [ ] 2.5 Saved thread features preservation property test
    - **Property 2: Preservation** - Saved Thread Functionality
    - **IMPORTANT**: Follow observation-first methodology
    - Observe behavior on UNFIXED code: saved threads show polling status, unsave functionality, and saved date information
    - Write property-based test: for all saved threads, polling status, unsave functionality, and saved date continue to display
    - Verify test passes on UNFIXED code
    - _Requirements: 3.5_

- [ ] 3. Fix for thread card click URL handling bugs

  - [ ] 3.1 Implement thread card click non-responsiveness fix
    - **File**: `app/src/main/java/com/chan/mimi/ui/screens/threads/ThreadListScreen.kt`
    - Verify ThreadCard component properly passes onClick handler to ChanCard
    - Ensure onClick = { onThreadClick(thread) } is correctly implemented at line 1067
    - **File**: `app/src/main/java/com/chan/mimi/ui/screens/saved/SavedScreen.kt`
    - Fix SavedThreadCard click handling - remove onClick = null from ChanCard call at line 352
    - Ensure combinedClickable(onClick = onClick, onLongClick = { showMenu = true }) works correctly
    - _Bug_Condition: isBugCondition1(input) where input.screen ∈ {ThreadListScreen, SavedScreen} AND input.cardType = "threadCard"_
    - _Expected_Behavior: Expected to navigate to thread detail view with visual feedback_
    - _Preservation: Must preserve image click functionality (3.1)_
    - _Requirements: 2.1, 3.1_

  - [ ] 3.2 Implement URL non-interactivity fix
    - **File**: `app/src/main/java/com/chan/mimi/ui/components/ChanHtmlText.kt`
    - Enhance URL regex to catch more URL patterns (improve detection)
    - Ensure URL annotations are properly added in appendTextWithUrlAnnotations function
    - Verify link styling (underlined blue) is applied to detected URLs
    - Test onClick handler at lines 116-135 properly handles URL_TAG annotations
    - _Bug_Condition: isBugCondition2(input) where input.containsUrl = true AND input.displayMode = "plainText"_
    - _Expected_Behavior: Expected to display as underlined blue clickable links opening in browser_
    - _Preservation: Must preserve reply link navigation (>>postNo) functionality (3.2)_
    - _Requirements: 2.2, 3.2_

  - [ ] 3.3 Implement missing post three-dot menu fix
    - **File**: `app/src/main/java/com/chan/mimi/ui/screens/threads/ThreadDetailScreen.kt`
    - Ensure PostCard is called with showOverflowMenu = true at line 394
    - Verify PostCard component correctly renders menu when showOverflowMenu = true (lines 716-728)
    - Test that menu appears with correct options: copy post link, copy text, open post link, share post
    - _Bug_Condition: isBugCondition3(input) where input.screen = "ThreadDetailScreen" AND input.cardComponent = "PostCard" AND input.menuOptions = ∅_
    - _Expected_Behavior: Expected to show three-dot menu with options: copy post link, copy text, open post link, share post_
    - _Preservation: Must preserve existing menu functionality in ThreadListScreen (3.3)_
    - _Requirements: 2.3, 3.3_

  - [ ] 3.4 Implement deleted post interaction blocking fix
    - **File**: `app/src/main/java/com/chan/mimi/ui/screens/saved/SavedThreadDetailScreen.kt`
    - Pass allowDeletedInteractions = true to PostCard when displaying saved threads with deleted posts
    - Ensure onReplyClick is not null for deleted posts
    - Remove blocking conditions for deleted post interactions
    - Allow reply viewing and text reading for deleted posts
    - _Bug_Condition: isBugCondition4(input) where input.postStatus = "deleted" AND input.screen = "SavedScreen" AND input.interactionAllowed = false_
    - _Expected_Behavior: Expected to allow clicking on deleted posts to view replies and read replied text_
    - _Preservation: Must preserve non-deleted post interactions (3.4)_
    - _Requirements: 2.4, 3.4_

  - [ ] 3.5 Implement missing thread list three-dot menu fix
    - **File**: `app/src/main/java/com/chan/mimi/ui/screens/saved/SavedScreen.kt`
    - Add visible three-dot menu icon to SavedThreadCard (similar to ThreadCard lines 1070-1115)
    - Implement IconButton with DropdownMenu for thread-level options
    - Provide consistent menu options: copy thread link, copy text, open thread link, share thread, save/unsave functionality
    - _Bug_Condition: isBugCondition5(input) where input.screen ∈ {"ThreadListScreen", "SavedScreen"} AND input.cardType = "threadCard" AND input.menuOptions = ∅_
    - _Expected_Behavior: Expected to show three-dot menu with thread-specific actions_
    - _Preservation: Must preserve existing ThreadListScreen menu (3.3) and saved thread features (3.5)_
    - _Requirements: 2.5, 3.3, 3.5_

  - [ ] 3.6 Verify bug condition exploration tests now pass
    - **Property 1: Expected Behavior** - Thread Card Click Responsiveness
    - **IMPORTANT**: Re-run the SAME test from task 1.1 - do NOT write a new test
    - The test from task 1.1 encodes the expected behavior
    - When this test passes, it confirms the expected behavior is satisfied
    - Run thread card click non-responsiveness exploration test
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.1_

    - **Property 1: Expected Behavior** - URL Interactivity in Posts
    - **IMPORTANT**: Re-run the SAME test from task 1.2 - do NOT write a new test
    - Run URL non-interactivity exploration test
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.2_

    - **Property 1: Expected Behavior** - Post Three-Dot Menu Presence
    - **IMPORTANT**: Re-run the SAME test from task 1.3 - do NOT write a new test
    - Run missing post three-dot menu exploration test
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.3_

    - **Property 1: Expected Behavior** - Deleted Post Interaction
    - **IMPORTANT**: Re-run the SAME test from task 1.4 - do NOT write a new test
    - Run deleted post interaction blocking exploration test
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.4_

    - **Property 1: Expected Behavior** - Thread List Three-Dot Menu
    - **IMPORTANT**: Re-run the SAME test from task 1.5 - do NOT write a new test
    - Run missing thread list three-dot menu exploration test
    - **EXPECTED OUTCOME**: Test PASSES (confirms bug is fixed)
    - _Requirements: 2.5_

  - [ ] 3.7 Verify preservation tests still pass
    - **Property 2: Preservation** - Image Click Navigation
    - **IMPORTANT**: Re-run the SAME tests from task 2.1 - do NOT write new tests
    - Run image click preservation property test
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - _Requirements: 3.1_

    - **Property 2: Preservation** - Reply Link Navigation
    - **IMPORTANT**: Re-run the SAME tests from task 2.2 - do NOT write new tests
    - Run reply link preservation property test
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - _Requirements: 3.2_

    - **Property 2: Preservation** - Existing Thread Card Menu
    - **IMPORTANT**: Re-run the SAME tests from task 2.3 - do NOT write new tests
    - Run existing menu preservation property test
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - _Requirements: 3.3_

    - **Property 2: Preservation** - Non-Deleted Post Interactions
    - **IMPORTANT**: Re-run the SAME tests from task 2.4 - do NOT write new tests
    - Run non-deleted post preservation property test
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - _Requirements: 3.4_

    - **Property 2: Preservation** - Saved Thread Functionality
    - **IMPORTANT**: Re-run the SAME tests from task 2.5 - do NOT write new tests
    - Run saved thread features preservation property test
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - _Requirements: 3.5_

- [ ] 4. Integration testing tasks

  - [ ] 4.1 Test full navigation flow integration
    - Test thread list → thread detail navigation with visual feedback
    - Verify thread card clicks produce ripple effect via ChanCard
    - Test navigation consistency across ThreadListScreen and SavedScreen
    - Ensure proper back navigation from thread detail views

  - [ ] 4.2 Test URL handling flow integration
    - Test post with URL → tap → browser opens
    - Verify various URL formats are detected and styled correctly
    - Test URL clicking produces proper browser intent
    - Ensure URL detection works with mixed content (text + URLs)

  - [ ] 4.3 Test menu functionality flow integration
    - Test three-dot menu → select option → expected action
    - Verify post menus work correctly in ThreadDetailScreen
    - Verify thread menus work correctly in ThreadListScreen and SavedScreen
    - Test menu options produce correct results (copy, open, share)

  - [ ] 4.4 Test saved thread flow integration
    - Test save thread → view saved → interact with deleted posts
    - Verify deleted posts allow interaction in saved threads
    - Test saved thread features (polling, unsave, date) continue working
    - Ensure consistency between ThreadListScreen and SavedScreen behavior

  - [ ] 4.5 Test cross-screen consistency integration
    - Test thread cards behave consistently across ThreadListScreen and SavedScreen
    - Verify menu implementations are consistent across components
    - Test navigation patterns are consistent across screens
    - Ensure visual feedback (ripple) is consistent for all clickable elements

- [ ] 5. Checkpoint - Ensure all tests pass
  - Run all exploration tests (should all pass after fixes)
  - Run all preservation tests (should all pass confirming no regressions)
  - Run all integration tests (should all pass confirming end-to-end functionality)
  - Ensure comprehensive test coverage for all 5 bug conditions and preservation requirements
  - Document any issues found and ensure they are resolved before marking complete