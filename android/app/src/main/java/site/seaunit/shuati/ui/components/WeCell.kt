package site.seaunit.shuati.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.seaunit.shuati.ui.theme.CellWhite
import site.seaunit.shuati.ui.theme.Ink
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.TextGray
import site.seaunit.shuati.ui.theme.TextHint

/**
 * 微信风格的一行内容：白底、左标题右说明、可选右侧箭头，行与行之间是发丝线。
 *
 * 所有列表页都复用它，保证"同样的东西在不同页面长得一样"。
 */
@Composable
fun WeCell(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    rightText: String? = null,
    rightColor: Color = TextGray,
    rightBold: Boolean = false,
    showChevron: Boolean = false,
    titleMaxLines: Int = 2,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().background(CellWhite)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Ink,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    maxLines = titleMaxLines,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = TextGray,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
            if (!rightText.isNullOrBlank()) {
                Text(
                    text = rightText,
                    color = rightColor,
                    fontSize = 14.sp,
                    fontWeight = if (rightBold) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                trailing()
            }
            if (showChevron) {
                Text(
                    text = "›",
                    color = TextHint,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (divider) {
            Hairline(startIndent = 16.dp)
        }
    }
}

/** 一行里的「值 + 单位」，用在点数这类需要强调数字的地方 */
@Composable
fun WeValue(text: String, color: Color = Ink, unit: String? = null) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.End) {
        Text(text = text, color = color, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (unit != null) {
            Text(
                text = " $unit",
                color = TextGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

/** 状态小标签（已上架 / 错误 这类），方角浅底，不抢标题 */
@Composable
fun WeTag(text: String, color: Color = Moss) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        modifier = Modifier
            .background(color.copy(alpha = 0.10f), androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
