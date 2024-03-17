import axios from "axios";
import { defineStore } from "pinia";

const backend = "http://localhost:8080";
let token = localStorage.getItem("token");

export const useReviewCommentStore = defineStore("reviewComment", {
  state: () => ({
    reviewCommentList: [],
    reviewReplyList: [],
    reviewCommentUpList:[],
  }),
  actions: {
    // 댓글 조회
    async getReviewCommentList(reviewIdx) {
      try {
        let response = await axios.get(
          backend + `/review/${reviewIdx}/comment`,
          {
            headers: {
              "Content-Type": "application/json",
            },
          }
        );

        this.reviewCommentList = response.data.result;
        console.log(this.reviewCommentList);
      } catch (e) {
        console.log(e);
      }
    },

    // 댓글 작성
    async createReviewComment(reviewCommentContent, reviewIdx) {
      try {
        const response = await axios.post(
          backend + `/review/${reviewIdx}/comment/create`,
          { reviewCommentContent: reviewCommentContent },
          {
            headers: {
              Authorization: `Bearer ${token}`,
              "Content-Type": "application/json",
            },
          }
        );
        console.log(response);
        console.log("게시판 댓글 작성 성공");
        window.location.href = `http://localhost:8081/review/${reviewIdx}`;
      } catch (error) {
        console.error("ERROR : ", error);
      }
    },

    // 댓글 수정
    async updateReviewComment(reviewCommentContent, commentIdx, reviewIdx) {
      try {
        if (!token) {
          throw new Error(
            "토큰이 없습니다. 사용자가 로그인되었는지 확인하세요."
          );
        }

        const response = await axios.patch(
          backend + `/review/${reviewIdx}/update/${commentIdx}`,
          { reviewCommentContent: reviewCommentContent },
          {
            headers: {
              Authorization: `Bearer ${token}`,
              "Content-Type": "application/json",
            },
          }
        );

        window.location.href = `http://localhost:8081/review/${reviewIdx}`;

        console.log(response);
        console.log("게시판 댓글 수정 성공");
      } catch (error) {
        console.error("수정 실패 : ", error);
      }
    },

    // 댓글 삭제
    async deleteReviewComment(commentIdx, reviewIdx) {
      try {
        if (!token) {
          throw new Error(
            "토큰이 없습니다. 사용자가 로그인되었는지 확인하세요."
          );
        }

        const response = await axios.delete(
          `${backend}/review/${reviewIdx}/delete/${commentIdx}`,
          {
            headers: {
              Authorization: `Bearer ${token}`,
              "Content-Type": "application/json",
            },
          }
        );

        console.log(response);
        console.log("게시판 댓글 삭제");
        window.location.href = `http://localhost:8081/review/${reviewIdx}`;
      } catch (error) {
        console.error("삭제 실패 : ", error);
      }
    },

    //  대댓글 작성
    async createReviewReply(reviewReplyContent,reviewIdx, reviewCommentIdx) {
      try {
        const response = await axios.post(
          backend + `/review/${reviewIdx}/comment/create/${reviewCommentIdx}`,
          { reviewReplyContent: reviewReplyContent },
          {
            headers: {
              Authorization: `Bearer ${token}`,
              "Content-Type": "application/json",
            },
          }
        );
        console.log(response);
        console.log("게시판 대댓글 작성 성공");
        window.location.href = `http://localhost:8081/review/${reviewIdx}`;
      } catch (error) {
        console.error("ERROR : ", error);
      }
    },


      // 댓글 추천
      async reviewRecommend(commentIdx) {
        try {
          await axios.post(
            backend + "/reviewcomment/up/create",
            { reviewCommentIdx: commentIdx },
            {
              headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/json",
              },
            }
          );
          console.log("댓글 추천 성공");
          window.location.reload();
        } catch (error) {
          console.log(commentIdx);
          console.error("에러 : ", error);
        }
      },

      // 댓글 추천 삭제
      async cancelReviewComment(commentIdx) {
        try {
          await axios.patch(
            backend + `/reviewcomment/up/delete/${commentIdx}`,
            {},
            {
              headers: {
                Authorization: `Bearer ${token}`,
                "Content-Type": "application/json",
              },
            }
          );
          console.log("댓글 추천 취소 성공");
          window.location.reload();
        } catch (e) {
          console.log(commentIdx);
          console.error(e);
          throw e;
        }
      },
    },


});