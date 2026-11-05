[font=微软雅黑][size=3]
[align=center][size=5][color=#923E51][b]月魇 · Lunar Dread[/b][/color][/size]

十六月相 · 活体血月 · 异常生物 · 独立运行

[color=#777777]Minecraft 1.21.1 ｜ NeoForge 21.1.265 ｜ Java 21 ｜ 预览版 0.12.0[/color]

[url=https://github.com/vdjango/lunar-dread/releases/tag/v0.12.0][color=#6862A1]下载模组[/color][/url] · [url=https://github.com/vdjango/lunar-dread/issues][color=#6862A1]反馈问题[/color][/url] · [url=https://github.com/vdjango/lunar-dread][color=#6862A1]项目主页[/color][/url]

[/align]

[hr][size=4][color=#923E51][b]模组介绍[/b][/color][/size]


[b]月魇[/b]是一款以月相变化为核心的恐怖生存模组，加入 16 种月相、血月浓雾、异常重力和原创生物。不同月相会带来不同的增益与危险，既有适合收集物资的夜晚，也有需要躲避追猎、互相救援的夜晚。

[b]单独安装即可自动运行，无需其他模组或数据包。[/b]支持单人和多人游玩，可选择概率触发或百日进程。首次进入世界会获得《求生手册》和《月相异闻录》，在游戏中就能查看生存提示。

[img]https://raw.githubusercontent.com/vdjango/lunar-dread/v0.12.0/docs/images/giant-heartbeat.gif[/img]


[hr][size=4][color=#923E51][b]玩法看点[/b][/color][/size]


[list]
[*][b]16 种月相[/b]：丰收月、蓝月带来收益；血月、紫狱月、倒悬月等拥有各自的事件与遭遇。
[*][b]活体血月[/b]：月面脉动、心跳声、凝望追猎，以及带有预警的月面射线。
[*][b]地表与洞穴雾效[/b]：雾气随入夜渐浓，黎明散去；高处、地表与地下的视野各不相同。
[*][b]失重与救援[/b]：异常重力会让实体漂浮、视角翻滚；队友可用鱼钩协助救援。
[*][b]原创生物与异常动物[/b]：追击、掳走、挖掘、窥视等遭遇，攻击带有前摇与冷却。
[*][b]守卫补给[/b]：货箱从空中降落，提供坐标与方向指引，击败守卫后领取物资。
[/list]

[img]https://raw.githubusercontent.com/vdjango/lunar-dread/v0.12.0/docs/images/moon-phases.gif[/img]

[url=https://github.com/vdjango/lunar-dread/blob/v0.12.0/docs/images/moons/README.md][color=#6862A1]查看十六月相独立动图[/color][/url] · [url=https://github.com/vdjango/lunar-dread/releases/download/v0.12.0/lunar-dread-0.12.0-moon-gifs.zip][color=#6862A1]下载 GIF 素材包[/color][/url]

[img]https://raw.githubusercontent.com/vdjango/lunar-dread/v0.12.0/docs/images/gravity-tumble.gif[/img]


[hr][size=4][color=#923E51][b]两种玩法[/b][/color][/size]


[table=98%]
[tr][td][color=#A5783F][b]模式[/b][/color][/td][td][color=#A5783F][b]玩法[/b][/color][/td][/tr]
[tr][td][b]概率模式 · 随时开玩[/b][/td][td]独立安装默认使用。每晚有 35% 概率出现特殊月相，其余为普通月；不限天数，事件强度不随存档年龄增加。[/td][/tr]
[tr][td][b]百日模式 · 逐周挑战[/b][/td][td]五天一周，难度逐周提升。前五天准备，第十天起每五天血月，血月后两晚休整，第 100 天巨月；百日结束后停止自然事件。[/td][/tr]
[/table]

[b]两种模式都能独立游玩。[/b]《百日围城·断墙之夜》默认使用百日模式，并保留整合包配套玩法；单独安装不会附带整合包里的其他模组。

[table=98%]
[tr][td][color=#A5783F][b]房主命令[/b][/color][/td][td][color=#A5783F][b]作用[/b][/color][/td][/tr]
[tr][td][font=Consolas]/lunardread mode[/font][/td][td]查看当前模式[/td][/tr]
[tr][td][font=Consolas]/lunardread mode probability[/font][/td][td]切换为概率模式[/td][/tr]
[tr][td][font=Consolas]/lunardread mode hundred_days[/font][/td][td]切换为百日模式[/td][/tr]
[/table]

切换需要管理员权限，保留当前世界日期；每晚的月相随世界保存，重启不会重新抽签。


[hr][size=4][color=#923E51][b]安装说明[/b][/color][/size]


[b]Minecraft 1.21.1 / NeoForge，Java 21。暂无 Forge、Fabric 版本。[/b]

[list=1]
[*]创建 NeoForge 21.1.265 实例。
[*]将 [font=Consolas]lunar-dread-0.12.0.jar[/font] 放入 [font=Consolas]mods[/font] 文件夹，进入世界即可自动运行。
[*]多人游戏时，客户端与房主或服务端都安装月魇，版本保持一致。
[/list]

[b]旧版玩家：[/b]0.12.0 起，Enhanced Celestials、Stellar View、Zombies Break & Build、The Hordes 均为可选兼容，不再是月魇的必需前置。


[hr][size=4][color=#923E51][b]常用配置[/b][/color][/size]


首次运行后生成配置文件。[font=Consolas]true[/font] 为开启，[font=Consolas]false[/font] 为关闭；修改前退出游戏，保存后重新进入。

[color=#A5783F][b]个人画面与声音[/b][/color]

文件：[font=Consolas]config/lunar_dread-client.toml[/font]

[table=98%]
[tr][td][color=#A5783F][b]配置项[/b][/color][/td][td][color=#A5783F][b]默认值[/b][/color][/td][td][color=#A5783F][b]作用[/b][/color][/td][/tr]
[tr][td][font=Consolas]visuals.moonFogEnabled[/font][/td][td][font=Consolas]true[/font][/td][td]开关月相雾效[/td][/tr]
[tr][td][font=Consolas]visuals.bloodFogPeakDistance[/font][/td][td][font=Consolas]7.0[/font][/td][td]血月地表可见距离，数值越大看得越远[/td][/tr]
[tr][td][font=Consolas]visuals.bloodCaveFogDistance[/font][/td][td][font=Consolas]5.0[/font][/td][td]血月地下可见距离，数值越大看得越远[/td][/tr]
[tr][td][font=Consolas]audio.tensionMusicEnabled[/font][/td][td][font=Consolas]true[/font][/td][td]开关紧张配乐[/td][/tr]
[/table]

[color=#A5783F][b]世界规则[/b][/color]

文件：实例的 [font=Consolas]config/lunar_dread-server.toml[/font]，由房主或服主修改，联机玩家自动使用同一规则。

[table=98%]
[tr][td][color=#A5783F][b]配置项[/b][/color][/td][td][color=#A5783F][b]默认值[/b][/color][/td][td][color=#A5783F][b]作用[/b][/color][/td][/tr]
[tr][td][font=Consolas]calendar.mode[/font][/td][td][font=Consolas]PROBABILITY[/font][/td][td]概率模式；改为 [font=Consolas]HUNDRED_DAYS[/font] 使用百日模式[/td][/tr]
[tr][td][font=Consolas]probability.eventChance[/font][/td][td][font=Consolas]0.35[/font][/td][td]每晚特殊月相总概率，0.35为35%[/td][/tr]
[tr][td][font=Consolas]probability.graceDays[/font][/td][td][font=Consolas]0[/font][/td][td]开局保护天数，0表示第一晚即可触发[/td][/tr]
[tr][td][font=Consolas]probability.repeatGap[/font][/td][td][font=Consolas]2[/font][/td][td]同一种特殊月相至少间隔几晚[/td][/tr]
[tr][td][font=Consolas]probability.difficultyDay[/font][/td][td][font=Consolas]30[/font][/td][td]概率模式的固定强度，范围6–100[/td][/tr]
[tr][td][font=Consolas]probability.giantChance[/font][/td][td][font=Consolas]0.25[/font][/td][td]抽中血月时，有25%概率直接出现巨月[/td][/tr]
[tr][td][font=Consolas]encounters.horrorEnabled[/font][/td][td][font=Consolas]true[/font][/td][td]开关原创恐怖遭遇[/td][/tr]
[tr][td][font=Consolas]moon.heartbeatEnabled[/font][/td][td][font=Consolas]true[/font][/td][td]开关血月心跳声[/td][/tr]
[tr][td][font=Consolas]animals.chaseDurationScale[/font][/td][td][font=Consolas]1.0[/font][/td][td]异常动物追击时长倍率，越小越短[/td][/tr]
[/table]

[b]挑选月相：[/b][font=Consolas][probability.weights][/font] 下的数值决定各月相出现的比例，设为 [font=Consolas]0[/font] 可禁用对应月相，例如 [font=Consolas]blood = 0[/font] 禁用概率血月。这些概率设置只作用于概率模式。

表中的 [font=Consolas]visuals.xxx[/font] 表示 [font=Consolas][visuals][/font] 分组下的 [font=Consolas]xxx[/font]，其他分组同理。心跳等敌对音效也可通过游戏内的“敌对生物”音量调整。


[hr][size=4][color=#923E51][b]下载与反馈[/b][/color][/size]


[url=https://github.com/vdjango/lunar-dread/releases][color=#6862A1]前往下载[/color][/url] · [url=https://github.com/vdjango/lunar-dread/issues][color=#6862A1]提交问题[/color][/url]

反馈时请附上游戏版本、月魇版本、日志或截图，方便定位问题。

作者：[b]BuggCraft[/b] · 月魇代码采用 [b]MIT[/b] 协议。
[/size][/font]
