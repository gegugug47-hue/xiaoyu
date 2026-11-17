package com.xiaoyu.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaoyu.app.R
import com.xiaoyu.app.ui.theme.Tokens

/**
 * 系统表情面板（执行书 M4-02）：8 大类，每类一个 tab，点选后直接插入输入框。
 * 数据内联（不依赖 emoji_categories.json），覆盖常用表情。
 */
private val EMOJI_CATEGORIES = listOf(
    R.string.sticker_emoji_category_smiley to listOf("😀", "😃", "😄", "😁", "😆", "🥹", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😔", "😪", "🤗", "🤭", "🤫", "🤔", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😴", "😌"),
    R.string.sticker_emoji_category_animal to listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🦗", "🕷", "🦂", "🐢", "🐍", "🦎", "🦖", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈"),
    R.string.sticker_emoji_category_food to listOf("🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🌽", "🌶", "🥕", "🧄", "🧅", "🥔", "🍞", "🥐", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥓", "🥞", "🧇", "🥩", "🍗", "🍖", "🦴", "🌭", "🍔", "🍟", "🍕", "🥪", "🌮", "🌯", "🥙"),
    R.string.sticker_emoji_category_activity to listOf("⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🪃", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸", "🥌", "🎿", "⛷", "🏂", "🪂", "🏋", "🤼", "🤸", "⛹", "🤺", "🤾", "🏌", "🏇", "🧘", "🏄", "🏊", "🤽", "🚣", "🧗", "🚵"),
    R.string.sticker_emoji_category_travel to listOf("🚗", "🚕", "🚙", "🚌", "🏎", "🚓", "🚑", "🚒", "🚐", "🚚", "🚛", "🚜", "🏍", "🛵", "🚲", "🛴", "🚏", "🛣", "🛤", "🚨", "🚔", "🚍", "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋", "🚞", "🚂", "🚆", "🚄", "🚅", "🚈", "🚇", "🚊", "🚉", "✈", "🛫", "🛬", "🛩", "💺", "🚀", "🛸", "🚁", "🛶", "⛵", "🚤", "🛳", "⛴"),
    R.string.sticker_emoji_category_object to listOf("⌚", "📱", "📲", "💻", "⌨", "🖥", "🖨", "🖱", "💽", "💾", "💿", "📀", "📷", "📸", "📹", "🎥", "📽", "🎞", "📞", "☎", "📟", "📠", "📺", "📻", "🎙", "🎚", "🎛", "🔋", "🔌", "💡", "🔦", "🕯", "🪔", "🧯", "🛢", "💸", "💵", "💴", "💶", "💷", "💰", "💳", "💎", "⚖", "🔧", "🔨", "⚒", "🛠", "⛏", "🪚"),
    R.string.sticker_emoji_category_symbol to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "♥", "💌", "💋", "💍", "💄", "💋", "👓", "🕶", "👔", "👕", "👖", "🧣", "🧤", "🧥", "🧦", "👗", "👘", "🥻", "🩱", "🩲", "🩳", "👙", "👚", "👛", "👜", "🛍", "🎒", "👞", "👟", "🥾", "🥿"),
    R.string.sticker_emoji_category_gesture to listOf("👋", "🤚", "🖐", "✋", "🖖", "👌", "🤏", "✌", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝", "👎", "👍", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🦷", "🦴", "👀", "👁", "👅", "👄", "💋", "🩸"),
)

@Composable
fun EmojiPanel(onPick: (String) -> Unit) {
    var category by remember { mutableStateOf(EMOJI_CATEGORIES.first().first) }
    val emojis = EMOJI_CATEGORIES.first { it.first == category }.second
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EMOJI_CATEGORIES.forEach { (labelRes, _) ->
                FilterChip(
                    selected = labelRes == category,
                    onClick = { category = labelRes },
                    label = { Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = Tokens.SizeTouchTarget),
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(emojis) { emoji ->
                Box(
                    modifier = Modifier
                        .size(Tokens.SizeTouchTarget)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPick(emoji) }
                        .semantics { contentDescription = emoji },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
            }
        }
    }
}
