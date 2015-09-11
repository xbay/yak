#Yak设计文档

##概述

Yak是一个用scala编写的服务程序，用来把数据库转换成一系列事件流，目前的计划里，它仅支持MongoDB/Tokumx。

把数据库转换成事件流，目的是用来把数据库里的数据同步到搜索引擎。

##问题的提出

在通常的系统设计里，搜索引擎是一个独立于主数据库的服务，搜索引擎的数据是主数据库的冗余。并且，搜索引擎的数据和主数据库的表结构并非一一对应。

以一个简单的书签应用为例，每个用户拥有若干书签。用户信息里包含一系列的tags，用来标识用户的兴趣，比如"IT产品"，“旅游”等。主数据库的数据结构大概如下：
	
	用户
	{
		"id": 1,
		"name": "xxx",
		"tags": ["IT产品", "旅游"],
	}
	
	书签
	{
		"id": 12,
		"url": "https://github.com/UniBell/yak",
		"desc": "一个scala项目",
		"user_id": 1
	}

为了搜索具有某个标签的用户的书签，我们一般在搜索引擎里把用户信息嵌入到书签里。搜索引擎里的数据一般是这个样子：

	{
		"id": 12,
		"url": "https://github.com/UniBell/yak",
		"desc": "一个scala项目",
		"user": {
			"id": 1,
			"name": "xxx",
			"tags": ["IT产品", "旅游"],
		}
	}
	
为了保持搜索引擎的数据和主数据库同步，我们需要在书签或者用户数据有修改（比如修改了书签的desc或者用户的tags）的时候，把搜索引擎的对应数据对象重写一遍。

可以用硬编码的方式来实现，把同步搜索引擎的代码写在业务代码里，在修改用户的时候把该用户的书签读出，写回搜索引擎，同样的在修改书签的时候也需要把书签数据写回搜索引擎，这个做法会导致往搜索引擎写书签数据的代码存在两个不同的地方。

为了让代码更清晰，可以用事件通知的方式，在修改主数据库的地方并不同时写搜索引擎，而是发出一个信号，然后在一个统一的地方监听数据库修改信号，根据信号里的信息把相应的数据从数据库读出并写回搜索引擎。代码逻辑大概是这样的：

	修改用户
	function user_add_tag(id, new_tag) 
	{
		db.user.update({"id": id}, {$append: {"tags": new_tag}})
		.onSuccess(function(res) {
			EventBus.send({
				"category": "user",
				"id": id
			});
		});
	}
	
	修改书签
	function bookmark_modify_desc(id, new_desc) 
	{
		db.bookmark.update({"id": id}, {$set: {"desc": new_desc}})
		.onSuccess(function(res) {
			EventBus.send({
				"category": "bookmark",
				"id": id
			});
		});
	}
	
	事件处理
	EventBus
	.get()
	.filter(function(category){
		return(category == "user");
	}).handle(function(event) {
		sync_bookmark_for_user(event['id']);
	})
	
	EventBus
	.get()
	.filter(function(category){
		return(category == "bookmark");
	}).handle(function(event) {
		sync_bookmark(event['id']);
	})
	
这是一种实现策略和实现机制分离的方法。能工作得很好，不过还欠缺一点 -- 这个方式只能同步“新”数据，对于之前遗留的数据只能通过一个外部脚本在系统启动之前预先同步到搜索引擎。

如果我们能有一个方式，把数据库表上的所有历史操作和数据，变成一个事件流，再结合上面的通过事件通知来同步数据的机制，就能在运行时把数据同步到搜索引擎，并在这之后一直保持同步。

##实现的机制

对于MongoDB/Tokumx来说，每个库都有一个binlog记录，里边保存了所有数据库表在一段时间内（collection）的修改记录，根据默认设置，“一段时间”是14天。

所以我们可以通过不停的读binlog记录来监控所有collection的改动。

