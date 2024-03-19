package com.example.bootshelf.boardsvc.board.model.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GetListBoardRes {

    private Integer idx;

    private String nickName;

    private String userProfileImage;

    private String title;

    private String content;

    private Integer boardCategoryIdx;

    private String boardImg;

    private List<String> tagNameList;

    private Integer viewCnt;

    private Integer upCnt;

    private Integer scrapCnt;

    private Integer commentCnt;

    private String type;

    private String boardType;

    private String createdAt;

    private String updatedAt;
}
