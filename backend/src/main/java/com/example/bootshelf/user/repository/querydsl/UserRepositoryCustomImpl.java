package com.example.bootshelf.user.repository.querydsl;


import com.example.bootshelf.course.Course;
import com.example.bootshelf.course.QCourse;
import com.example.bootshelf.user.model.entity.QUser;
import com.example.bootshelf.user.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.QuerydslRepositorySupport;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class UserRepositoryCustomImpl extends QuerydslRepositorySupport implements UserRepositoryCustom {
    public UserRepositoryCustomImpl() {
        super(User.class);
    }


    @Override
    public Optional<User> findUser(String email) {
        QUser user = new QUser("user");

        Optional<User> result = Optional.ofNullable(from(user)
                .where(user.email.eq(email))
                .fetchOne()
        );
        return result;
    }

    @Override
    public Optional<Course> findProgramName(String programName) {
        QCourse course = new QCourse("course");

        Optional<Course> result = Optional.ofNullable(from(course)
                .where(course.programName.contains(programName))
                .fetchOne());

        return result;
    }
}
