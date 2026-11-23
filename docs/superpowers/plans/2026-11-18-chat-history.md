# Chat History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement paginated single-user conversation/message history plus rename and transactional physical delete.

**Architecture:** A dedicated history controller and service use the existing MyBatis-Plus mappers. MyBatis-Plus handles database paging, Hutool handles page metadata, and project VOs isolate HTTP responses from persistence types.

**Tech Stack:** Spring Boot 4.1.2, Jakarta Validation, MyBatis-Plus 3.5.16, PostgreSQL, Spring transactions, Hutool 5.8.39, Lombok, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-11-18-chat-history-design.md`

## Global Constraints

- Single-user mode; do not add authentication or ownership fields.
- Page numbers are one-based.
- `pageSize` defaults to 20 and is limited to 100.
- Rename titles are trimmed before validation and must contain 1 to 50 characters.
- Delete is physical and transactional.
- No new database migration or foreign key.
- Preserve all existing chat and network-search behavior.

---

### Task 1: History API Value Objects

**Files:**
- Create: `src/main/java/com/feiniao/airobot/model/vo/chat/PageReqVO.java`
- Create: `src/main/java/com/feiniao/airobot/model/vo/chat/PageRspVO.java`
- Create: `src/main/java/com/feiniao/airobot/model/vo/chat/ChatHistoryRspVO.java`
- Create: `src/main/java/com/feiniao/airobot/model/vo/chat/ChatMessageRspVO.java`
- Create: `src/main/java/com/feiniao/airobot/model/vo/chat/RenameChatReqVO.java`
- Test: `src/test/java/com/feiniao/airobot/model/vo/chat/ChatHistoryValueObjectTest.java`

**Interfaces:**
- Consumes: Lombok and Jakarta Validation.
- Produces: `PageReqVO`, `PageRspVO<T>`, `ChatHistoryRspVO`, `ChatMessageRspVO`, `RenameChatReqVO`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void pageDefaultsToOneAndTwenty() {
    PageReqVO request = new PageReqVO();
    assertEquals(1L, request.getPageNum());
    assertEquals(20L, request.getPageSize());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ChatHistoryValueObjectTest test`

Expected: compilation failure because `PageReqVO` does not exist.

- [ ] **Step 3: Implement the value objects**

```java
@Data
public class PageReqVO {
    @Min(value = 1, message = "页码不能小于 1")
    private Long pageNum = 1L;

    @Min(value = 1, message = "每页数量不能小于 1")
    @Max(value = 100, message = "每页数量不能超过 100")
    private Long pageSize = 20L;
}
```

`PageRspVO<T>` exposes `pageNum`, `pageSize`, `total`, `totalPages`, and
`records`. `ChatHistoryRspVO` exposes `uuid`, `summary`, `createTime`, and
`updateTime`. `ChatMessageRspVO` exposes `id`, `chatUuid`, `role`, `content`,
and `createTime`. `RenameChatReqVO` has a nonblank `title`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ChatHistoryValueObjectTest test`

Expected: PASS.

### Task 2: Conversation Page Query

**Files:**
- Create: `src/main/java/com/feiniao/airobot/service/ChatHistoryService.java`
- Create: `src/main/java/com/feiniao/airobot/service/impl/ChatHistoryServiceImpl.java`
- Test: `src/test/java/com/feiniao/airobot/service/ChatHistoryServiceImplTest.java`

**Interfaces:**
- Consumes: `ChatMapper`, `ChatMessageMapper`, `PageReqVO`.
- Produces: `PageRspVO<ChatHistoryRspVO> listChatHistory(PageReqVO request)`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void listChatHistoryMapsMyBatisPageToHutoolMetadata() {
    Page<ChatDO> databasePage = new Page<>(1, 20);
    databasePage.setTotal(21);
    databasePage.setRecords(List.of(chat));
    when(chatMapper.selectPage(any(), any())).thenReturn(databasePage);

    PageRspVO<ChatHistoryRspVO> result =
            service.listChatHistory(new PageReqVO());

    assertEquals(1L, result.getPageNum());
    assertEquals(21L, result.getTotal());
    assertEquals(2L, result.getTotalPages());
    assertEquals(chat.getUuid(), result.getRecords().getFirst().getUuid());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ChatHistoryServiceImplTest#listChatHistoryMapsMyBatisPageToHutoolMetadata test`

Expected: compilation failure because the service does not exist.

- [ ] **Step 3: Implement the query**

Use MyBatis-Plus `Page<>(pageNum, pageSize)` and a lambda query ordered by
`updateTime DESC, id DESC`. Copy records into Hutool `PageResult`, calculate
total pages with `PageUtil.totalPage`, and convert to `PageRspVO`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ChatHistoryServiceImplTest#listChatHistoryMapsMyBatisPageToHutoolMetadata test`

Expected: PASS.

### Task 3: Message Page Query

**Files:**
- Modify: `src/main/java/com/feiniao/airobot/service/ChatHistoryService.java`
- Modify: `src/main/java/com/feiniao/airobot/service/impl/ChatHistoryServiceImpl.java`
- Test: `src/test/java/com/feiniao/airobot/service/ChatHistoryServiceImplTest.java`

**Interfaces:**
- Produces: `PageRspVO<ChatMessageRspVO> listChatMessages(String chatUuid, PageReqVO request)`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void listChatMessagesReturnsNewestPageInChronologicalOrder() {
    Page<ChatMessageDO> databasePage = new Page<>(1, 2);
    databasePage.setTotal(3);
    databasePage.setRecords(List.of(newerMessage, olderMessage));
    when(chatMapper.selectOne(any())).thenReturn(chat);
    when(chatMessageMapper.selectPage(any(), any())).thenReturn(databasePage);

    PageRspVO<ChatMessageRspVO> result =
            service.listChatMessages(chat.getUuid(), new PageReqVO());

    assertEquals(olderMessage.getId(), result.getRecords().get(0).getId());
    assertEquals(newerMessage.getId(), result.getRecords().get(1).getId());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ChatHistoryServiceImplTest#listChatMessagesReturnsNewestPageInChronologicalOrder test`

Expected: compilation failure because the method does not exist.

- [ ] **Step 3: Implement the query**

Reject a missing conversation with `CHAT_NOT_FOUND`. Query messages by chat UUID
ordered by `createTime DESC, id DESC`, reverse the returned records, then map
and paginate them.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ChatHistoryServiceImplTest#listChatMessagesReturnsNewestPageInChronologicalOrder test`

Expected: PASS.

### Task 4: Rename Conversation

**Files:**
- Modify: `src/main/java/com/feiniao/airobot/ResponseCodeEnum.java`
- Modify: `src/main/java/com/feiniao/airobot/service/ChatHistoryService.java`
- Modify: `src/main/java/com/feiniao/airobot/service/impl/ChatHistoryServiceImpl.java`
- Test: `src/test/java/com/feiniao/airobot/service/ChatHistoryServiceImplTest.java`

**Interfaces:**
- Produces: `ChatHistoryRspVO renameChat(String chatUuid, RenameChatReqVO request)`.

- [ ] **Step 1: Write the failing tests**

```java
@Test
void renameChatTrimsTitleAndRefreshesUpdateTime() {
    when(chatMapper.selectOne(any())).thenReturn(chat);
    when(chatMapper.update(any(), any())).thenReturn(1);

    ChatHistoryRspVO result = service.renameChat(
            chat.getUuid(), new RenameChatReqVO("  new title  "));

    assertEquals("new title", result.getSummary());
    assertTrue(result.getUpdateTime().isAfter(chat.getUpdateTime()));
}

@Test
void renameChatRejectsOverlongTitleAfterTrim() {
    RenameChatReqVO request = new RenameChatReqVO("x".repeat(51));

    BizException exception = assertThrows(
            BizException.class,
            () -> service.renameChat(chat.getUuid(), request));

    assertEquals(ResponseCodeEnum.CHAT_TITLE_INVALID.getErrorCode(),
            exception.getErrorCode());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -q -Dtest=ChatHistoryServiceImplTest#renameChatTrimsTitleAndRefreshesUpdateTime+renameChatRejectsOverlongTitleAfterTrim test`

Expected: compilation failure because rename behavior does not exist.

- [ ] **Step 3: Implement rename**

Load the conversation, trim the title, count Unicode code points, enforce 1 to
50, update `summary` and `update_time`, verify one row was updated, and return
the updated history VO.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -q -Dtest=ChatHistoryServiceImplTest test`

Expected: PASS.

### Task 5: Transactional Delete

**Files:**
- Modify: `src/main/java/com/feiniao/airobot/service/ChatHistoryService.java`
- Modify: `src/main/java/com/feiniao/airobot/service/impl/ChatHistoryServiceImpl.java`
- Test: `src/test/java/com/feiniao/airobot/service/ChatHistoryServiceImplTest.java`

**Interfaces:**
- Produces: `void deleteChat(String chatUuid)`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void deleteChatDeletesMessagesBeforeConversation() {
    when(chatMapper.selectOne(any())).thenReturn(chat);
    when(chatMessageMapper.delete(any())).thenReturn(2);
    when(chatMapper.delete(any())).thenReturn(1);

    service.deleteChat(chat.getUuid());

    InOrder order = inOrder(chatMessageMapper, chatMapper);
    order.verify(chatMessageMapper).delete(any());
    order.verify(chatMapper).delete(any());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ChatHistoryServiceImplTest#deleteChatDeletesMessagesBeforeConversation test`

Expected: compilation failure because delete behavior does not exist.

- [ ] **Step 3: Implement delete**

Annotate the service method with
`@Transactional(rollbackFor = Exception.class)`. Require the conversation to
exist, delete messages by `chatUuid`, delete the conversation by `uuid`, and
reject if the final delete affects no rows.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ChatHistoryServiceImplTest#deleteChatDeletesMessagesBeforeConversation test`

Expected: PASS.

### Task 6: HTTP Controller

**Files:**
- Create: `src/main/java/com/feiniao/airobot/controller/ChatHistoryController.java`
- Modify: `src/main/java/com/feiniao/airobot/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/feiniao/airobot/controller/ChatHistoryControllerTest.java`

**Interfaces:**
- Consumes: `ChatHistoryService`.
- Produces: four routes under `/chat/history`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void listHistoryReturnsServicePage() {
    when(chatHistoryService.listChatHistory(any())).thenReturn(page);

    Response<PageRspVO<ChatHistoryRspVO>> response =
            controller.listHistory(new PageReqVO());

    assertSame(page, response.getData());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ChatHistoryControllerTest test`

Expected: compilation failure because the controller does not exist.

- [ ] **Step 3: Implement the controller**

Add GET list, GET messages, PATCH title, and DELETE handlers. Wrap service
results with `Response.success`. Validate `PageReqVO` and `RenameChatReqVO`.
Extend `GlobalExceptionHandler` for bind and constraint validation exceptions.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ChatHistoryControllerTest test`

Expected: PASS.

### Task 7: Conversation Activity Timestamp

**Files:**
- Modify: `src/main/java/com/feiniao/airobot/advisor/CustomStreamLoggerAndMessage2DBAdvisor.java`
- Modify: `src/main/java/com/feiniao/airobot/controller/ChatController.java`
- Test: `src/test/java/com/feiniao/airobot/controller/ChatControllerTest.java`

**Interfaces:**
- Consumes: `ChatMapper`.
- Produces: message persistence that also refreshes `t_chat.update_time`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void completedStreamRefreshesConversationUpdateTime() {
    ChatMapper chatMapper = mock(ChatMapper.class);
    TransactionTemplate transactionTemplate = new TransactionTemplate() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    };
    when(chatMapper.update(isNull(), any())).thenReturn(1);

    // Configure the controller with mocks and a real transaction callback,
    // subscribe to the empty model stream, then assert the update side effect.
    verify(chatMapper).update(isNull(), any());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -Dtest=ChatControllerTest#completedStreamRefreshesConversationUpdateTime test`

Expected: FAIL because the controller does not provide a `ChatMapper` to the advisor.

- [ ] **Step 3: Implement the timestamp update**

Pass `ChatMapper` into `CustomStreamLoggerAndMessage2DBAdvisor`. In the existing
completion transaction, update `ChatDO.updateTime` by UUID and throw when the
conversation no longer exists so the message inserts roll back.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -Dtest=ChatControllerTest test`

Expected: PASS.

### Task 8: Full Verification

**Files:**
- Test: all existing and new tests.

- [ ] **Step 1: Run the focused test suite**

Run: `mvn -q -Dtest=ChatHistoryServiceImplTest,ChatHistoryControllerTest,ChatControllerTest test`

Expected: PASS with zero failures.

- [ ] **Step 2: Run the complete suite**

Run: `mvn -q test`

Expected: PASS with zero failures.

- [ ] **Step 3: Inspect the final changed files**

Run: `rg -n "ChatHistory|listChatHistory|listChatMessages|renameChat|deleteChat" src/main src/test`

Expected: controller, service, VOs, error code, tests, and activity timestamp
integration are all present.
