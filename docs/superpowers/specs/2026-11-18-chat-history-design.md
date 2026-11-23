# Chat History Feature Design

## Goal

Add single-user chat history APIs for listing conversations, paging messages,
renaming conversations, and permanently deleting a conversation with all of its
messages.

## Scope

- Single-user deployment; no authentication or ownership columns.
- Four REST APIs under `/chat/history`.
- One-based page numbers with a default page size of 20 and a maximum of 100.
- Conversation list ordered by `update_time DESC, id DESC`.
- Message page 1 contains the newest messages. Records within each page are
  returned in chronological order.
- Rename titles are trimmed and must contain 1 to 50 characters.
- Delete is physical and transactional: delete messages by `chat_uuid`, then
  delete the conversation by `uuid`.

## Architecture

`ChatHistoryController` exposes the HTTP contract and wraps results with the
existing `Response<T>`. `ChatHistoryService` owns history behavior, while its
implementation uses the existing MyBatis-Plus mappers.

Database pagination uses MyBatis-Plus `Page<DO>`. Hutool `PageResult<T>` and
`PageUtil.totalPage(...)` are used to calculate and carry pagination metadata.
A project-owned `PageRspVO<T>` converts the Hutool result into a stable JSON
object because `PageResult<T>` extends `ArrayList<T>`.

The streaming persistence advisor updates `t_chat.update_time` in the same
transaction that saves the user message and assistant response. This keeps the
history list ordered by actual conversation activity.

## API Contract

### List Conversations

`GET /chat/history?pageNum=1&pageSize=20`

Returns a page of:

```json
{
  "uuid": "conversation-uuid",
  "summary": "conversation title",
  "createTime": "2026-11-18 21:00:00",
  "updateTime": "2026-11-18 21:10:00"
}
```

### List Messages

`GET /chat/history/{chatUuid}/messages?pageNum=1&pageSize=20`

Returns a page of:

```json
{
  "id": 1,
  "chatUuid": "conversation-uuid",
  "role": "user",
  "content": "message content",
  "createTime": "2026-11-18 21:00:00"
}
```

### Rename Conversation

`PATCH /chat/history/{chatUuid}/title`

```json
{
  "title": "new title"
}
```

Returns the updated conversation.

### Delete Conversation

`DELETE /chat/history/{chatUuid}`

Returns a successful empty `Response`.

## Error Handling

- Invalid page parameters return `PARAM_NOT_VALID`.
- A missing conversation returns `CHAT_NOT_FOUND`.
- A null, blank, or overlong title after trimming returns `CHAT_TITLE_INVALID`.
- Deletion failures roll back both message and conversation deletion.
- Existing `GlobalExceptionHandler` remains the response boundary.

## Persistence Changes

No schema migration is introduced. Existing indexes cover conversation update
time and message chat UUID. The streaming advisor will additionally update the
parent conversation's `update_time`.

## Technology Stack

- Spring Boot 4.1.2 MVC
- Jakarta Validation
- MyBatis-Plus 3.5.16
- PostgreSQL
- Spring transaction management
- Hutool 5.8.39
- Lombok
- JUnit 5 and Mockito

## Testing

- Conversation page mapping, ordering, and pagination metadata.
- Newest-first message pages with chronological order within each page.
- Missing conversation behavior.
- Trimmed rename and update time refresh.
- Invalid title rejection.
- Transactional deletion of messages and conversation.
- Advisor update of conversation activity time.
- Existing chat and network-search tests remain green.
