package me.ash.reader.ui.page.home.feeds

/**
 * A stable `LazyColumn` key for a group row. `group.id` is a Room primary key, so it survives
 * reordering and unrelated list updates, whereas the default `itemsIndexed` key (the index)
 * does not - inserting a group used to reshuffle every key below it and discard the whole
 * subtree's remembered state.
 *
 * The prefix keeps these keys out of the same namespace as the plain `item {}` rows above and
 * below the groups.
 */
fun feedsGroupKey(groupId: String): String = "feeds-group:$groupId"
