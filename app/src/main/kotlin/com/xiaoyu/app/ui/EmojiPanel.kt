package com.xiaoyu.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 系统表情面板（执行书 M4-02）：8 大类，每类一个 tab，点选后直接插入输入框。
 * 数据内联（不依赖 emoji_categories.json），覆盖常用表情。
 */
private val EMOJI_CATEGORIES = listOf(
    "笑脸" to listOf("😀", "😃", "😄", "😁", "😆", "🥹", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😔", "😪", "🤗", "🤭", "🤫", "🤔", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😴", "😌"),
    "动物" to listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🐔", "🐧", "🐦", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🦗", "🕷", "🦂", "🐢", "🐍", "🦎", "🦖", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈"),
    "食物" to listOf("🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🌽", "🌶", "🥕", "🧄", "🧅", "🥔", "🍞", "🥐", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥓", "🥞", "🧇", "🥩", "🍗", "🍖", "🦴", "🌭", "🍔", "🍟", "🍕", "🥪", "🌮", "🌯", "🥙"),
    "活动" to listOf("⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🪃", "🥅", "⛳", "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷", "⛸", "🥌", "🎿", "⛷", "🏂", "🪂", "🏋", "🤼", "🤸", "⛹", "🤺", "🤾", "🏌", "🏇", "🧘", "🏄", "🏊", "🤽", "🚣", "🧗", "🚵"),
    "旅行" to listOf("🚗", "🚕", "🚙", "🚌", "🏎", "🚓", "🚑", "🚒", "🚐", "🚚", "🚛", "🚜", "🏍", "🛵", "🚲", "🛴", "🚏", "🛣", "🛤", "🚨", "🚔", "🚍", "🚘", "🚖", "🚡", "🚠", "🚟", "🚃", "🚋", "🚞", "🚂", "🚆", "🚄", "🚅", "🚈", "🚇", "🚊", "🚉", "✈", "🛫", "🛬", "🛩", "💺", "🚀", "🛸", "🚁", "🛶", "⛵", "🚤", "🛳", "⛴"),
    "物品" to listOf("⌚", "📱", "📲", "💻", "⌨", "🖥", "🖨", "🖱", "💽", "💾", "💿", "📀", "📷", "📸", "📹", "🎥", "📽", "🎞", "📞", "☎", "📟", "📠", "📺", "📻", "🎙", "🎚", "🎛", "🔋", "🔌", "💡", "🔦", "🕯", "🪔", "🧯", "🛢", "💸", "💵", "💴", "💶", "💷", "💰", "💳", "💎", "⚖", "🔧", "🔨", "⚒", "🛠", "⛏", "🪚"),
    "符号" to listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "♥", "💌", "💋", "💍", "💄", "💋", "👓", "🕶", "👔", "👕", "👖", "🧣", "🧤", "🧥", "🧦", "👗", "👘", "🥻", "🩱", "🩲", "🩳", "👙", "👚", "👛", "👜", "🛍", "🎒", "👞", "👟", "🥾", "🥿"),
    "手势" to listOf("👋", "🤚", "🖐", "✋", "🖖", "👌", "🤏", "✌", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝", "👎", "👍", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🦷", "🦴", "👀", "👁", "👅", "👄", "💋", "🩸"),
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
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EMOJI_CATEGORIES.forEach { (name, _) ->
                FilterChip(
                    selected = name == category,
                    onClick = { category = name },
                    label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(emojis) { emoji ->
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPick(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, fontSize = 22.sp)
                }
            }
        }
    }
}
