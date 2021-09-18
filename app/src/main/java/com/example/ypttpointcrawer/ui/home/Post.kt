package com.example.ypttpointcrawer.ui.home

import java.util.*

class Post {
    var poster:String = ""
    var title:String = ""
    var postDate:String = ""
    var uniqueKey = {"$title $postDate"}

}