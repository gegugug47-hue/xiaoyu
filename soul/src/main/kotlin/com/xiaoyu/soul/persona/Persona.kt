package com.xiaoyu.soul.persona

/** 身份层：她是谁（长期不变）。 */
data class PersonaIdentity(
    val id: Long,
    val name: String,
    val contentMarkdown: String,
)

/** 风格层：她怎么说话（可随时切换）。 */
data class PersonaStyle(
    val id: Long,
    val name: String,
    val contentMarkdown: String,
)

data class Persona(
    val identity: PersonaIdentity,
    val style: PersonaStyle,
)
