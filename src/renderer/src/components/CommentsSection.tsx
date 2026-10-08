import { useState } from "react";
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { CornerDownRight, MessageSquare, Send, ThumbsDown, ThumbsUp, UserRound } from "lucide-react";
import type { SourceComment, SourceInfo } from "@shared/types";
import { hibiki } from "@/lib/hibiki";
import { ErrorBanner } from "@/components/ErrorBanner";
import { splitMentions } from "@/lib/commentText";
import { useSignInPrompt } from "@/components/SignInPrompt";
import { cn } from "@/lib/cn";

/**
 * A title's comments, from the source itself.
 *
 * Everything below the network was already in place - the extension contract has listComments and
 * postComment, YummyAnime implements both, and main already forwards them - and nothing in the app
 * ever showed them. This is that screen.
 *
 * Reading needs no account and posting does, which is the source's rule, not this component's: it
 * asks for an account only where one is required, so a signed-out visitor still gets the thread.
 *
 * Nothing here is hidden or disabled for want of one. A signed-out visitor sees the same box and the
 * same buttons, and pressing them asks for the account (see SignInPrompt) - a comment field that is
 * simply absent, and vote buttons that are simply dead, both read as the feature being broken.
 */
export function CommentsSection({ source, animeId }: { source: SourceInfo; animeId: string }) {
  const { t } = useTranslation();
  const sourceId = source.id;
  const hasComments = source.capabilities.includes("COMMENTS");

  // Same key the source settings dialog signs in and out under, so this section knows about it the
  // moment that happens rather than a refetch later.
  const account = useQuery({
    queryKey: ["sourceAccount", sourceId],
    queryFn: () => hibiki.sources.account.get(sourceId),
    enabled: hasComments && source.capabilities.includes("ACCOUNT"),
  });

  const thread = useInfiniteQuery({
    queryKey: ["comments", sourceId, animeId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => hibiki.sources.comments.list(sourceId, { animeId, offset: pageParam }),
    // The source decides its own page size, and says it is done by answering with nothing - so the
    // next offset is however many have arrived, not a page count times a size this end guessed.
    getNextPageParam: (lastPage, allPages) =>
      lastPage.length === 0 ? undefined : allPages.reduce((count, page) => count + page.length, 0),
    enabled: hasComments,
  });

  if (!hasComments) return null;

  // A source with comments but no account of its own has nobody to sign in as: there is nothing to
  // prompt for, so its actions go straight through and it answers for them itself.
  const signedIn = !!account.data || !source.capabilities.includes("ACCOUNT");
  const comments = thread.data?.pages.flat() ?? [];

  return (
    <section className="px-8 pt-10 mobile:px-4 mobile:pt-8">
      <h2 className="mb-4 flex items-center gap-2 text-xl font-bold tracking-[-.02em] text-text">
        <MessageSquare className="h-[18px] w-[18px] text-muted" strokeWidth={2} />
        {t("detail.comments.title")}
      </h2>

      <Composer source={source} animeId={animeId} signedIn={signedIn} />

      {thread.isError && <ErrorBanner message={(thread.error as Error).message} />}
      {thread.isPending && <CommentsSkeleton />}
      {!thread.isPending && comments.length === 0 && !thread.isError && (
        <p className="py-6 text-sm text-muted">{t("detail.comments.empty")}</p>
      )}

      <div className="flex flex-col gap-5">
        {comments.map((comment) => (
          <Comment key={comment.id} comment={comment} source={source} animeId={animeId} signedIn={signedIn} />
        ))}
      </div>

      {thread.hasNextPage && (
        <button
          onClick={() => thread.fetchNextPage()}
          disabled={thread.isFetchingNextPage}
          className="mt-6 rounded-xl border border-border px-4 py-2.5 text-sm font-semibold text-text/80 transition-colors hover:bg-text/[.06] hover:text-text disabled:opacity-60"
        >
          {thread.isFetchingNextPage ? t("detail.comments.loading") : t("detail.comments.loadMore")}
        </button>
      )}
    </section>
  );
}

function Comment({
  comment,
  source,
  animeId,
  signedIn,
  isReply = false,
}: {
  comment: SourceComment;
  source: SourceInfo;
  animeId: string;
  signedIn: boolean;
  isReply?: boolean;
}) {
  const { t, i18n } = useTranslation();
  const sourceId = source.id;
  const promptSignIn = useSignInPrompt();
  const [repliesOpen, setRepliesOpen] = useState(false);
  const [replying, setReplying] = useState(false);

  // Replies are their own request, made only when someone asks for them: a thread page already
  // carries the count, and fetching every branch of every comment up front would be one request per
  // comment for branches most people never open.
  const replies = useQuery({
    queryKey: ["comments", sourceId, animeId, comment.id],
    queryFn: () => hibiki.sources.comments.list(sourceId, { animeId, parentId: comment.id }),
    enabled: repliesOpen,
  });

  return (
    <article className={cn("flex gap-3", isReply && "ml-6")}>
      <Avatar name={comment.authorName} url={comment.authorAvatarUrl} />
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="truncate text-sm font-semibold text-text">{comment.authorName}</span>
          <span className="shrink-0 text-xs text-muted">
            {new Date(comment.createdAt).toLocaleDateString(i18n.language, { day: "numeric", month: "short", year: "numeric" })}
          </span>
        </div>
        <div className="mt-1 select-text whitespace-pre-wrap text-sm leading-relaxed [overflow-wrap:anywhere] text-text/90">
          <CommentText text={comment.text} />
        </div>

        <div className="mt-1.5 flex items-center gap-4">
          <Votes source={source} animeId={animeId} comment={comment} signedIn={signedIn} />
          {
            <button
              onClick={() => (signedIn ? setReplying((open) => !open) : promptSignIn(source))}
              className="inline-flex items-center gap-1 text-xs font-semibold text-muted transition-colors hover:text-text"
            >
              <CornerDownRight className="h-3 w-3" strokeWidth={2.5} />
              {t("detail.comments.reply")}
            </button>
          }
          {(comment.replyCount ?? 0) > 0 && (
            <button
              onClick={() => setRepliesOpen((open) => !open)}
              className="text-xs font-semibold text-muted transition-colors hover:text-text"
            >
              {repliesOpen
                ? t("detail.comments.hideReplies")
                : t("detail.comments.showReplies", { count: comment.replyCount ?? 0 })}
            </button>
          )}
        </div>

        {replying && (
          <Composer
            source={source}
            animeId={animeId}
            signedIn={signedIn}
            parentId={comment.id}
            compact
            onPosted={() => {
              setReplying(false);
              setRepliesOpen(true);
            }}
          />
        )}

        {repliesOpen && (
          <div className="mt-4 flex flex-col gap-4 border-l border-border pl-1">
            {replies.isPending && <p className="text-xs text-muted">{t("detail.comments.loading")}</p>}
            {replies.isError && <ErrorBanner message={(replies.error as Error).message} />}
            {(replies.data ?? []).map((reply) => (
              <Comment key={reply.id} comment={reply} source={source} animeId={animeId} signedIn={signedIn} isReply />
            ))}
          </div>
        )}
      </div>
    </article>
  );
}

/**
 * The box itself is the same signed in or out. What changes is where pressing send goes: to the
 * source, or to the sign-in prompt. The draft is left in the field either way, so coming back from
 * signing in means pressing send again rather than typing it again.
 */
function Composer({
  source,
  animeId,
  signedIn,
  parentId,
  compact = false,
  onPosted,
}: {
  source: SourceInfo;
  animeId: string;
  signedIn: boolean;
  parentId?: string;
  compact?: boolean;
  onPosted?: () => void;
}) {
  const { t } = useTranslation();
  const sourceId = source.id;
  const queryClient = useQueryClient();
  const promptSignIn = useSignInPrompt();
  const [text, setText] = useState("");

  const post = useMutation({
    mutationFn: () => hibiki.sources.comments.post(sourceId, { animeId, text: text.trim(), parentId: parentId ?? null }),
    onSuccess: async () => {
      setText("");
      // Refetched rather than spliced in: the source assigns the id, the timestamp and the author
      // name, and a locally invented row would differ from the one everyone else sees.
      await queryClient.invalidateQueries({ queryKey: ["comments", sourceId, animeId] });
      onPosted?.();
    },
  });

  const canSubmit = text.trim().length > 0 && !post.isPending;
  const submit = () => {
    if (!canSubmit) return;
    if (!signedIn) return promptSignIn(source);
    post.mutate();
  };

  return (
    <form
      className={cn("mb-6", compact && "mb-0 mt-3")}
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
    >
      {/* The send control sits inside the field rather than under it. A button on its own line
          added a block of empty space between this box and whatever follows it, which read as the
          thread being misaligned rather than as a form having a button. */}
      <div className="relative">
        <textarea
          value={text}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={(event) => {
            // Enter sends, Shift+Enter breaks the line - the shape every comment box has. Without
            // it the only way to send is a mouse trip to a corner of the field.
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              submit();
            }
          }}
          rows={compact ? 2 : 3}
          placeholder={t(parentId ? "detail.comments.replyPlaceholder" : "detail.comments.placeholder")}
          className="w-full resize-none rounded-xl border border-border bg-text/[.03] py-2.5 pl-3 pr-12 text-sm text-text outline-none transition-colors placeholder:text-muted focus:border-accent/70"
        />
        <button
          type="submit"
          disabled={!canSubmit}
          aria-label={t("detail.comments.post")}
          title={t("detail.comments.post")}
          className={cn(
            "absolute bottom-2.5 right-2.5 flex h-8 w-8 items-center justify-center rounded-lg transition-colors",
            canSubmit ? "bg-accent text-accent-fg hover:brightness-110" : "bg-text/[.06] text-muted",
          )}
        >
          <Send className="h-4 w-4" strokeWidth={2.25} />
        </button>
      </div>
      {post.isError && <ErrorBanner className="mt-2" message={(post.error as Error).message} />}
    </form>
  );
}

/**
 * One comment's up and down votes.
 *
 * Counts come from the source and so does the viewer's own vote, which is what lets a pressed
 * button look pressed. Pressing the one already chosen takes the vote back, the way every site with
 * these buttons behaves.
 *
 * Signed out, the buttons still show the counts and still respond - they ask for the account. They
 * used to be `disabled`, which is the one state that looks identical to a feature that is broken.
 */
function Votes({
  source,
  animeId,
  comment,
  signedIn,
}: {
  source: SourceInfo;
  animeId: string;
  comment: SourceComment;
  signedIn: boolean;
}) {
  const sourceId = source.id;
  const promptSignIn = useSignInPrompt();
  const queryClient = useQueryClient();
  const [pending, setPending] = useState<number | null>(null);

  const vote = useMutation({
    mutationFn: (next: number) => hibiki.sources.comments.vote(sourceId, { commentId: comment.id, vote: next }),
    onMutate: (next) => setPending(next),
    // Refetched rather than counted locally: the source owns these numbers, and a vote that was
    // refused (a deleted comment, a rate limit) must not leave a number here that nobody else sees.
    onSettled: async () => {
      await queryClient.invalidateQueries({ queryKey: ["comments", sourceId, animeId] });
      setPending(null);
    },
  });

  const cast = (next: number) => (signedIn ? vote.mutate(next) : promptSignIn(source));
  const current = pending ?? comment.viewerVote ?? 0;
  const likes = (comment.likes ?? 0) + (pending === 1 && comment.viewerVote !== 1 ? 1 : 0);
  const dislikes = (comment.dislikes ?? 0) + (pending === -1 && comment.viewerVote !== -1 ? 1 : 0);

  return (
    <span className="flex items-center gap-1">
      <VoteButton
        icon={ThumbsUp}
        count={likes}
        active={current === 1}
        disabled={vote.isPending}
        onClick={() => cast(current === 1 ? 0 : 1)}
        activeClassName="text-emerald-400"
      />
      <VoteButton
        icon={ThumbsDown}
        count={dislikes}
        active={current === -1}
        disabled={vote.isPending}
        onClick={() => cast(current === -1 ? 0 : -1)}
        activeClassName="text-rose-400"
      />
    </span>
  );
}

function VoteButton({
  icon: Icon,
  count,
  active,
  disabled,
  onClick,
  activeClassName,
}: {
  icon: typeof ThumbsUp;
  count: number;
  active: boolean;
  disabled: boolean;
  onClick: () => void;
  activeClassName: string;
}) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={cn(
        "inline-flex items-center gap-1 rounded-md px-1.5 py-0.5 text-xs font-semibold transition-colors",
        active ? activeClassName : "text-muted",
        disabled ? "cursor-default" : "hover:bg-text/[.06] hover:text-text",
      )}
    >
      <Icon className={cn("h-3.5 w-3.5", active && "fill-current")} strokeWidth={2.25} />
      {count > 0 ? count : ""}
    </button>
  );
}

function CommentText({ text }: { text: string }) {
  return (
    <>
      {splitMentions(text).map((part, index) =>
        part.type === "mention" ? (
          <span key={index} className="font-semibold text-accent-text">@{part.name}</span>
        ) : part.type === "spoiler" ? (
          <details key={index} className="inline">
            <summary className="inline cursor-pointer rounded bg-text/[.12] px-1.5 py-0.5 text-xs font-semibold text-text/80 marker:content-none hover:bg-text/[.18]">
              {part.label}
            </summary>
            <span className="rounded bg-text/[.08] px-1 py-0.5">{part.value}</span>
          </details>
        ) : (
          part.value
        ),
      )}
    </>
  );
}

function Avatar({ name, url }: { name: string; url?: string | null }) {
  return (
    <div className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-text/[.07]">
      {url ? (
        <img src={url} alt="" className="h-full w-full object-cover" />
      ) : name ? (
        <span className="text-sm font-bold text-muted">{name.slice(0, 1).toUpperCase()}</span>
      ) : (
        <UserRound className="h-4 w-4 text-muted" strokeWidth={2} />
      )}
    </div>
  );
}

function CommentsSkeleton() {
  return (
    <div className="flex flex-col gap-5">
      {Array.from({ length: 3 }).map((_, index) => (
        <div key={index} className="flex animate-pulse gap-3">
          <div className="h-9 w-9 shrink-0 rounded-full bg-text/[.06]" />
          <div className="flex-1">
            <div className="h-3.5 w-32 rounded bg-text/[.07]" />
            <div className="mt-2.5 h-3 w-full rounded bg-text/[.05]" />
            <div className="mt-1.5 h-3 w-3/5 rounded bg-text/[.05]" />
          </div>
        </div>
      ))}
    </div>
  );
}
