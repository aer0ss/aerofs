/**
  AeroNode

  This class stores the statuses (eg: "Downloading", "Uploading", "Conflicted", etc..) that a file has.
  AeroNodes form a tree structure that reproduces the file system tree, and they also hold the combined
  status of all child nodes that they have.

  This allows efficient "bubbling up" of statuses. For example, if one single file deep down the hierarchy
  has the Downloading status, then all parents up to the root node will also have this status. This allows
  the shell extension to display overlay icons on folder that reflect the status of the files inside it.
  (if any file is downloading, the parent folder is overlayed as "downloading" too.)

  The whole point of this structure is to make it very efficient to query the status of a file (ie: to call
  the status() method), because we do this operation hundreds of times, for each file that the Shell displays
  to the user.
*/

#pragma once

#include <string>
#include <map>
#include <vector>

class AeroNode
{
public:

	typedef enum {
		NoStatus             = 0,
		Downloading          = 1 << 0,
		Uploading            = 1 << 1
	} Status;

	AeroNode(const std::wstring& name, AeroNode* parent);
	~AeroNode();

	AeroNode* nodeAtPath(const std::wstring& path, bool createPath);
	void setStatus(Status status);

	std::wstring name() const;
	AeroNode* parent() const;
	Status status() const;
	std::map<std::wstring, AeroNode*> children();

private:
	void updateChildrenStatus();
	AeroNode* AeroNode::nodeAtPath(const std::vector<std::wstring>& pathComponents, int index, bool create);
	std::wstring pathOfNode() const;

	const std::wstring m_name;
	AeroNode* const m_parent;
	std::map<std::wstring, AeroNode*> m_children;
	Status m_ownStatus;
	Status m_childrenStatus;
};

// Implement the operators that we use, otherwise we get compiler warnings about casting from int to Status

inline AeroNode::Status operator|(AeroNode::Status a, AeroNode::Status b)
{
	return AeroNode::Status(int(a) | int(b));
}

inline AeroNode::Status operator|=(AeroNode::Status& a, const AeroNode::Status& b)
{
	return a = a | b;
}

inline AeroNode::Status operator&(AeroNode::Status a, AeroNode::Status b)
{
	return AeroNode::Status(int(a) & int(b));
}

inline AeroNode::Status operator~(AeroNode::Status a)
{
	return AeroNode::Status(~int(a));
}
