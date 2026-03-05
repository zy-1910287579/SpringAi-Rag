package com.Storm.service.impl;

import com.Storm.entity.po.Course;
import com.Storm.mapper.CourseMapper;
import com.Storm.service.ICourseService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 学科表 服务实现类
 * </p>
 *
 * @author Storm
 * @since 2026-03-03
 */
@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements ICourseService {

}
